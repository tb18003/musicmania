/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.tb18003.musicmania.core.common.config.SpotifyConfig
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.datastore.token.AuthTokens
import io.github.tb18003.musicmania.core.datastore.token.TokenStore
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * OAuth 2.0 Authorization Code flow with PKCE (RFC 7636) following RFC 8252
 * (OAuth for native apps):
 *  - the login page opens in a Custom Tab (never a WebView), so the app never sees
 *    the user's Spotify credentials;
 *  - AppAuth generates a random `code_verifier` + S256 `code_challenge` and a `state`
 *    value and validates it on return (CSRF protection);
 *  - the redirect is a verified HTTPS App Link, and the code is useless without the
 *    verifier held in memory by this process;
 *  - no client secret exists in the app.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val config: SpotifyConfig,
    private val tokenStore: TokenStore,
    private val clock: Clock,
) {
    private val authService by lazy { AuthorizationService(context) }

    private val serviceConfig by lazy {
        AuthorizationServiceConfiguration(Uri.parse(config.authorizationEndpoint), Uri.parse(config.tokenEndpoint))
    }

    val isConfigured: Boolean get() = config.isConfigured

    /** Intent to launch with `ActivityResultContracts.StartActivityForResult`. */
    fun createAuthorizationIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            config.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(config.redirectUri),
        )
            .setScopes(config.scopes)
            // Always show the consent dialog so users can switch Spotify accounts.
            .setAdditionalParameters(mapOf("show_dialog" to "true"))
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /** Validates the redirect result and exchanges the code for tokens. */
    suspend fun handleAuthorizationResult(data: Intent?): DataResult<Unit> {
        if (data == null) return DataResult.Failure(AppError.Unknown("cancelled"))
        val exception = AuthorizationException.fromIntent(data)
        val response = AuthorizationResponse.fromIntent(data)
        if (exception != null || response == null) {
            return DataResult.Failure(exception.toAppError())
        }
        return when (val token = exchange(response)) {
            is DataResult.Failure -> token
            is DataResult.Success -> {
                val tokenResponse = token.data
                val access = tokenResponse.accessToken
                val refresh = tokenResponse.refreshToken
                if (access == null || refresh == null) {
                    DataResult.Failure(AppError.Unauthorized)
                } else {
                    tokenStore.save(
                        AuthTokens(
                            accessToken = access,
                            refreshToken = refresh,
                            expiresAtEpochMs = tokenResponse.accessTokenExpirationTime ?: (clock.millis() + DEFAULT_EXPIRY_MS),
                            scope = tokenResponse.scope,
                        ),
                    )
                    DataResult.Success(Unit)
                }
            }
        }
    }

    private suspend fun exchange(response: AuthorizationResponse): DataResult<TokenResponse> = suspendCancellableCoroutine { cont ->
        // Public client: AppAuth sends `client_id` + `code_verifier`, no secret.
        authService.performTokenRequest(response.createTokenExchangeRequest(), NoClientAuthentication.INSTANCE) { tokenResponse, ex ->
            if (!cont.isActive) return@performTokenRequest
            cont.resume(
                if (tokenResponse != null) DataResult.Success(tokenResponse) else DataResult.Failure(ex.toAppError()),
            )
        }
    }

    private fun AuthorizationException?.toAppError(): AppError = when {
        this == null -> AppError.Unknown(null)
        type == AuthorizationException.TYPE_GENERAL_ERROR &&
            code == AuthorizationException.GeneralErrors.NETWORK_ERROR.code -> AppError.Network
        code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code -> AppError.Unknown("cancelled")
        else -> AppError.Unauthorized
    }

    private companion object {
        const val DEFAULT_EXPIRY_MS = 3_600_000L
    }
}
