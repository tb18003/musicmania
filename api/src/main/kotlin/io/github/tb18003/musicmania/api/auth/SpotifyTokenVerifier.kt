/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.auth

import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class SpotifyUser(
    val id: String,
    val displayName: String?,
    /** The user's Spotify profile picture (https only), or null if they have none. */
    val avatarUrl: String? = null,
)

sealed interface TokenVerification {
    data class Valid(val user: SpotifyUser) : TokenVerification

    /** Expired, revoked, malformed, or not accepted by Spotify. */
    data object Invalid : TokenVerification

    /** Spotify could not answer (rate limited, down, timeout): neither valid nor invalid. */
    data class Unavailable(val retryAfterSeconds: Long?) : TokenVerification
}

/**
 * Validates a Spotify access token by asking Spotify who it belongs to (`GET /v1/me`). The app
 * already has this token, so users never log in twice. Results are cached by [TokenCache].
 */
class SpotifyTokenVerifier(
    private val apiBaseUrl: String,
    private val cache: TokenCache,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verify(token: String): TokenVerification {
        if (!looksLikeAToken(token)) return TokenVerification.Invalid
        cache.get(token)?.let { return it }
        return askSpotify(token).also { cache.put(token, it) }
    }

    private suspend fun askSpotify(token: String): TokenVerification {
        val request = HttpRequest.newBuilder(URI.create("$apiBaseUrl/v1/me"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .timeout(TIMEOUT)
            .GET()
            .build()
        val response = try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        } catch (_: IOException) {
            return TokenVerification.Unavailable(retryAfterSeconds = null)
        }
        return when (response.statusCode()) {
            HTTP_OK -> parse(response.body())
            // 403: the token is genuine but its owner is not allowed on the Spotify app (Development Mode).
            HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> TokenVerification.Invalid
            HTTP_TOO_MANY_REQUESTS -> TokenVerification.Unavailable(
                response.headers().firstValue("Retry-After").map(String::toLongOrNull).orElse(null),
            )
            else -> TokenVerification.Unavailable(retryAfterSeconds = null)
        }
    }

    private fun parse(body: String): TokenVerification = try {
        val me = json.decodeFromString(MeResponse.serializer(), body)
        if (SPOTIFY_ID.matches(
                me.id,
            )
        ) {
            TokenVerification.Valid(SpotifyUser(me.id, me.displayName, avatarOf(me.images)))
        } else {
            TokenVerification.Invalid
        }
    } catch (_: SerializationException) {
        TokenVerification.Unavailable(retryAfterSeconds = null)
    } catch (_: IllegalArgumentException) {
        TokenVerification.Unavailable(retryAfterSeconds = null)
    }

    /** The sharpest picture Spotify offers, if its URL is one the database accepts (https, bounded length). */
    private fun avatarOf(images: List<Image>): String? = images
        .filter { it.url.startsWith("https://") && it.url.length <= MAX_AVATAR_URL_LENGTH }
        .maxByOrNull { it.height ?: 0 }
        ?.url

    @Serializable
    private data class MeResponse(
        val id: String,
        @SerialName("display_name") val displayName: String? = null,
        val images: List<Image> = emptyList(),
    )

    @Serializable
    private data class Image(
        val url: String,
        val height: Int? = null,
    )

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(3)
        private const val MAX_TOKEN_LENGTH = 2048

        /** Same as the database's profiles.avatar_url check. */
        private const val MAX_AVATAR_URL_LENGTH = 2048
        private const val HTTP_OK = 200
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** Same rule as the database's allowlist.spotify_user_id check. */
        val SPOTIFY_ID = Regex("^[A-Za-z0-9._-]{1,128}$")
        private val TOKEN_CHARS = Regex("^[A-Za-z0-9._~+/=-]+$")

        /** Cheap local check: never send obvious garbage (or header injection attempts) to Spotify. */
        fun looksLikeAToken(token: String): Boolean = token.length in 1..MAX_TOKEN_LENGTH && TOKEN_CHARS.matches(token)
    }
}
