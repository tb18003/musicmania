/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.auth

import io.github.tb18003.musicmania.core.datastore.token.TokenStore
import kotlinx.coroutines.runBlocking
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-flight token refresh shared by [AuthInterceptor] (proactive, before expiry)
 * and [TokenAuthenticator] (reactive, after a 401).
 *
 * OkHttp calls both from its own blocking worker threads, so a JVM monitor is the
 * right primitive: when N requests fail concurrently only the first one refreshes,
 * the rest observe the new token and retry with it.
 */
@Singleton
class TokenRefreshCoordinator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher,
    private val clock: Clock,
) {
    private val lock = Any()

    /** A non-expired access token, refreshing first if needed. Null when signed out. */
    fun validAccessToken(): String? {
        val current = tokenStore.currentTokens() ?: return null
        if (!current.isExpired(clock.millis())) return current.accessToken
        return synchronized(lock) {
            val latest = tokenStore.currentTokens() ?: return null
            if (!latest.isExpired(clock.millis())) latest.accessToken else refreshLocked()
        }
    }

    /**
     * Called after the server rejected [failedAccessToken]. Returns a token to retry
     * with, or null if the request must fail.
     */
    fun refreshAfterUnauthorized(failedAccessToken: String?): String? = synchronized(lock) {
        val latest = tokenStore.currentTokens() ?: return null
        if (latest.accessToken != failedAccessToken) latest.accessToken else refreshLocked()
    }

    private fun refreshLocked(): String? {
        val current = tokenStore.currentTokens() ?: return null
        return when (val outcome = refresher.refresh(current)) {
            is RefreshOutcome.Success -> {
                runBlocking { tokenStore.save(outcome.tokens) }
                outcome.tokens.accessToken
            }
            RefreshOutcome.InvalidGrant -> {
                runBlocking { tokenStore.clear(sessionExpired = true) }
                null
            }
            RefreshOutcome.TransientFailure -> null
        }
    }
}
