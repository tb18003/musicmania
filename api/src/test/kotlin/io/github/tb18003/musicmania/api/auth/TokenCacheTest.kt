/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.auth

import com.github.benmanes.caffeine.cache.Ticker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class TokenCacheTest {
    private var nanos = 0L
    private val cache = TokenCache(ticker = Ticker { nanos })
    private val valid = TokenVerification.Valid(SpotifyUser("ana", "Ana"))

    private fun advance(duration: Duration) {
        nanos += duration.toNanos()
    }

    @Test
    fun `valid tokens are remembered for five minutes`() {
        cache.put("token", valid)
        advance(Duration.ofMinutes(4))
        assertEquals(valid, cache.get("token"))
        advance(Duration.ofMinutes(2))
        assertNull(cache.get("token"))
    }

    @Test
    fun `rejected tokens are remembered only briefly`() {
        cache.put("bad", TokenVerification.Invalid)
        assertEquals(TokenVerification.Invalid, cache.get("bad"))
        advance(Duration.ofSeconds(31))
        assertNull(cache.get("bad"))
    }

    @Test
    fun `spotify outages are never cached`() {
        cache.put("token", TokenVerification.Unavailable(retryAfterSeconds = 5))
        assertNull(cache.get("token"))
    }

    @Test
    fun `keys are hashes, never the token itself`() {
        val key = TokenCache.keyOf("secret-token")
        assertEquals(64, key.length)
        assertFalse(key.contains("secret"))
    }
}
