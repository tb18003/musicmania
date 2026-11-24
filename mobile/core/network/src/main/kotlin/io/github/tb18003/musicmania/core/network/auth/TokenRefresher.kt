/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.auth

import io.github.tb18003.musicmania.core.common.config.SpotifyConfig
import io.github.tb18003.musicmania.core.datastore.token.AuthTokens
import io.github.tb18003.musicmania.core.network.di.TokenHttpClient
import io.github.tb18003.musicmania.core.network.model.TokenResponseDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Clock
import javax.inject.Inject

sealed interface RefreshOutcome {
    data class Success(val tokens: AuthTokens) : RefreshOutcome

    /** The refresh token was revoked/expired: the user must sign in again. */
    data object InvalidGrant : RefreshOutcome

    /** Network or server problem: keep tokens, fail this request, try later. */
    data object TransientFailure : RefreshOutcome
}

fun interface TokenRefresher {
    /** Blocking call — only invoked from OkHttp worker threads. */
    fun refresh(current: AuthTokens): RefreshOutcome
}

/**
 * Refresh-token grant for a PKCE public client: only `client_id` is sent, never a
 * secret. Spotify may rotate the refresh token; if it is absent we keep the old one.
 */
internal class SpotifyTokenRefresher @Inject constructor(
    @param:TokenHttpClient private val client: OkHttpClient,
    private val config: SpotifyConfig,
    private val json: Json,
    private val clock: Clock,
) : TokenRefresher {
    override fun refresh(current: AuthTokens): RefreshOutcome {
        val request = Request.Builder()
            .url(config.tokenEndpoint)
            .post(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", current.refreshToken)
                    .add("client_id", config.clientId)
                    .build(),
            )
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val dto = json.decodeFromString(TokenResponseDto.serializer(), body)
                        RefreshOutcome.Success(
                            AuthTokens(
                                accessToken = dto.accessToken,
                                refreshToken = dto.refreshToken ?: current.refreshToken,
                                expiresAtEpochMs = clock.millis() + dto.expiresIn * MILLIS_PER_SECOND,
                                scope = dto.scope ?: current.scope,
                            ),
                        )
                    }
                    response.code in CLIENT_ERRORS && body.contains("invalid_grant") -> RefreshOutcome.InvalidGrant
                    else -> RefreshOutcome.TransientFailure
                }
            }
        } catch (_: IOException) {
            RefreshOutcome.TransientFailure
        } catch (_: SerializationException) {
            RefreshOutcome.TransientFailure
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        val CLIENT_ERRORS = 400..401
    }
}
