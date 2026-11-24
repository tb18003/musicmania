/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.auth

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import java.security.MessageDigest
import java.time.Duration

/**
 * Remembers recent token verifications so that every API call does not cost a Spotify request
 * (the Spotify quota is shared by the whole developer account).
 *
 * Keys are SHA-256 hashes: the cache never holds a token in clear text. Valid tokens are kept for
 * [validFor]; rejected ones for a shorter [invalidFor], so a client hammering with a bad token
 * cannot turn the API into a Spotify request amplifier. "Spotify unavailable" is never cached.
 */
class TokenCache(
    validFor: Duration = VALID_TTL,
    invalidFor: Duration = INVALID_TTL,
    maximumSize: Long = MAX_ENTRIES,
    ticker: Ticker = Ticker.systemTicker(),
) {
    private val valid: Cache<String, TokenVerification.Valid> =
        Caffeine.newBuilder().expireAfterWrite(validFor).maximumSize(maximumSize).ticker(ticker).build()
    private val invalid: Cache<String, TokenVerification.Invalid> =
        Caffeine.newBuilder().expireAfterWrite(invalidFor).maximumSize(maximumSize).ticker(ticker).build()

    fun get(token: String): TokenVerification? {
        val key = keyOf(token)
        return valid.getIfPresent(key) ?: invalid.getIfPresent(key)
    }

    fun put(token: String, verification: TokenVerification) {
        val key = keyOf(token)
        when (verification) {
            is TokenVerification.Valid -> valid.put(key, verification)
            TokenVerification.Invalid -> invalid.put(key, TokenVerification.Invalid)
            is TokenVerification.Unavailable -> Unit
        }
    }

    companion object {
        /** Well below a Spotify access token's one-hour lifetime. */
        private val VALID_TTL: Duration = Duration.ofMinutes(5)
        private val INVALID_TTL: Duration = Duration.ofSeconds(30)
        private const val MAX_ENTRIES = 10_000L

        fun keyOf(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
