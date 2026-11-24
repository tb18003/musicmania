/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.auth

import io.github.tb18003.musicmania.core.common.config.SpotifyConfig
import io.github.tb18003.musicmania.core.datastore.token.AuthTokens
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SpotifyTokenRefresherTest {
    private val server = MockWebServer()
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC)
    private val current = AuthTokens("old", "refresh-1", expiresAtEpochMs = 0, scope = "user-top-read")
    private lateinit var refresher: SpotifyTokenRefresher

    @Before
    fun setUp() {
        server.start()
        val config =
            SpotifyConfig(clientId = "client-123", redirectUri = "https://x/cb", tokenEndpoint = server.url("/api/token").toString())
        refresher = SpotifyTokenRefresher(OkHttpClient(), config, Json { ignoreUnknownKeys = true }, clock)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `successful refresh sends only client id and keeps rotated token`() {
        server.enqueue(
            MockResponse().setBody("""{"access_token":"new","token_type":"Bearer","expires_in":3600,"refresh_token":"refresh-2"}"""),
        )

        val outcome = refresher.refresh(current)

        assertIs<RefreshOutcome.Success>(outcome)
        assertEquals("new", outcome.tokens.accessToken)
        assertEquals("refresh-2", outcome.tokens.refreshToken)
        assertEquals(10_000 + 3_600_000L, outcome.tokens.expiresAtEpochMs)
        assertEquals("user-top-read", outcome.tokens.scope)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("client_id=client-123"))
        assertFalse(body.contains("client_secret"))
    }

    @Test
    fun `missing refresh token in response keeps the previous one`() {
        server.enqueue(MockResponse().setBody("""{"access_token":"new","expires_in":3600}"""))

        val outcome = refresher.refresh(current) as RefreshOutcome.Success

        assertEquals("refresh-1", outcome.tokens.refreshToken)
    }

    @Test
    fun `invalid grant is reported`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertEquals(RefreshOutcome.InvalidGrant, refresher.refresh(current))
    }

    @Test
    fun `server errors are transient`() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(RefreshOutcome.TransientFailure, refresher.refresh(current))
    }
}
