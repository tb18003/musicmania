/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.config

/** Runtime configuration, read from environment variables (Cloud Run injects them from Secret Manager). */
data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val spotifyApiBaseUrl: String,
    /** Browser origins allowed to call the API. Empty (the default) disables CORS entirely. */
    val corsOrigins: List<String>,
) {
    companion object {
        private const val DEFAULT_PORT = 8080
        private const val SPOTIFY_API = "https://api.spotify.com"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            fun required(name: String) = env[name]?.takeIf(String::isNotBlank) ?: error("Missing required environment variable $name")
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: DEFAULT_PORT,
                database = DatabaseConfig(
                    url = required("DATABASE_URL"),
                    user = required("DATABASE_USER"),
                    password = required("DATABASE_PASSWORD"),
                ),
                spotifyApiBaseUrl = env["SPOTIFY_API_BASE_URL"]?.trimEnd('/') ?: SPOTIFY_API,
                corsOrigins = env["CORS_ORIGINS"]?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
            )
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
) {
    /** Never let the password reach logs or error messages. */
    override fun toString(): String = "DatabaseConfig(url=$url, user=$user, password=<redacted>)"
}
