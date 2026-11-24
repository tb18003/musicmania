/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.auth

import io.github.tb18003.musicmania.api.db.AllowlistRepository
import io.github.tb18003.musicmania.api.http.ApiException
import io.github.tb18003.musicmania.api.model.Role
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.util.AttributeKey

/** The caller, once their Spotify token is verified and they are found in the allowlist. */
data class ApiUser(
    val spotifyUserId: String,
    val displayName: String?,
    val role: Role,
)

class Authenticator(
    private val verifier: SpotifyTokenVerifier,
    private val allowlist: AllowlistRepository,
) {
    /** 1) a bearer token is present, 2) Spotify says whose it is, 3) that person is allowlisted. */
    suspend fun authenticate(authorizationHeader: String?): ApiUser {
        val token = bearerToken(authorizationHeader) ?: throw ApiException.Unauthorized("missing_token")
        return requireAllowlisted(spotifyUserOf(token))
    }

    private suspend fun spotifyUserOf(token: String): SpotifyUser = when (val verification = verifier.verify(token)) {
        is TokenVerification.Valid -> verification.user
        TokenVerification.Invalid -> throw ApiException.Unauthorized("invalid_token")
        is TokenVerification.Unavailable -> throw ApiException.ServiceUnavailable(
            verification.retryAfterSeconds,
            "Spotify could not confirm the token right now. Try again later.",
        )
    }

    private suspend fun requireAllowlisted(user: SpotifyUser): ApiUser {
        val entry = allowlist.find(user.id)?.takeIf { it.enabled } ?: throw ApiException.Forbidden(
            "not_allowlisted",
            "This Spotify account is not allowed to use the MusicMania API. Ask the administrator to add it.",
        )
        return ApiUser(user.id, user.displayName ?: entry.displayName, entry.role)
    }

    private fun bearerToken(header: String?): String? {
        val (scheme, value) = header?.trim()?.split(' ', limit = 2)?.takeIf { it.size == 2 } ?: return null
        return value.trim().takeIf { scheme.equals("Bearer", ignoreCase = true) && it.isNotEmpty() }
    }
}

private val ApiUserKey = AttributeKey<ApiUser>("ApiUser")

/** Available inside routes protected by [SpotifyAuthentication]. */
val ApplicationCall.apiUser: ApiUser get() = attributes[ApiUserKey]

class SpotifyAuthenticationConfig {
    lateinit var authenticator: Authenticator
}

/**
 * Installed on the whole `/v1` route tree, so a new endpoint cannot forget authentication.
 * Failures become 401/403/503 problem responses through StatusPages.
 */
val SpotifyAuthentication = createRouteScopedPlugin("SpotifyAuthentication", ::SpotifyAuthenticationConfig) {
    val authenticator = pluginConfig.authenticator
    onCall { call ->
        call.attributes.put(ApiUserKey, authenticator.authenticate(call.request.headers[HttpHeaders.Authorization]))
    }
}
