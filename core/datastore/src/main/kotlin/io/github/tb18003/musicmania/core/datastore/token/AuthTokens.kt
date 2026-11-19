/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.token

import kotlinx.serialization.Serializable

/**
 * OAuth tokens. Only ever persisted encrypted (see [EncryptedTokenStore]).
 * `toString` is overridden so tokens can never leak into logs or crash reports.
 */
@Serializable
data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresAtEpochMs: Long, val scope: String? = null) {
    /** True if the access token expires within [skewMs] (refresh slightly early). */
    fun isExpired(nowEpochMs: Long, skewMs: Long = DEFAULT_SKEW_MS): Boolean = nowEpochMs + skewMs >= expiresAtEpochMs

    override fun toString(): String = "AuthTokens(expiresAtEpochMs=$expiresAtEpochMs, scope=$scope, tokens=<redacted>)"

    companion object {
        const val DEFAULT_SKEW_MS = 60_000L
    }
}
