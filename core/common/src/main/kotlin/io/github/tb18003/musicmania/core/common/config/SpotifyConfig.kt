/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.common.config

/**
 * Build-time Spotify configuration, provided by the `app` module from BuildConfig.
 * The client ID is a public identifier; no client secret exists in the app (PKCE).
 */
data class SpotifyConfig(
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String> = DEFAULT_SCOPES,
    val authorizationEndpoint: String = "https://accounts.spotify.com/authorize",
    val tokenEndpoint: String = "https://accounts.spotify.com/api/token",
    val apiBaseUrl: String = "https://api.spotify.com/v1/",
) {
    /** False when the developer has not configured credentials (demo mode only). */
    val isConfigured: Boolean get() = clientId.isNotBlank() && redirectUri.startsWith("https://")

    companion object {
        /** Least-privilege scopes: only what the features actually read or write. */
        val DEFAULT_SCOPES = listOf(
            "user-top-read",
            "user-read-recently-played",
            "user-read-currently-playing",
            "user-library-read",
            "playlist-read-private",
            "playlist-modify-private",
        )
    }
}
