/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.auth

import io.github.tb18003.musicmania.core.datastore.token.AuthTokens
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenAuthenticationTest {
    private val server = MockWebServer()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC)
    private val valid = AuthTokens("old", "refresh", expiresAtEpochMs = clock.millis() + 3_600_000)
    private val refreshCalls = AtomicInteger()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun client(store: FakeTokenStore, refresher: TokenRefresher, host: String = server.hostName): OkHttpClient {
        val coordinator = TokenRefreshCoordinator(store, refresher, clock)
        val allowed = AllowedApiHost(host)
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(coordinator, allowed))
            .authenticator(TokenAuthenticator(coordinator, allowed))
            .build()
    }

    private fun successRefresher(newToken: String = "new") = TokenRefresher { current ->
        refreshCalls.incrementAndGet()
        Thread.sleep(50) // widen the race window for the concurrency test
        RefreshOutcome.Success(current.copy(accessToken = newToken, expiresAtEpochMs = clock.millis() + 3_600_000))
    }

    /** 200 only for the "new" token, 401 otherwise. */
    private fun acceptOnly(token: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) =
            if (request.getHeader("Authorization") == "Bearer $token") MockResponse().setBody("ok") else MockResponse().setResponseCode(401)
    }

    private fun OkHttpClient.get() = newCall(Request.Builder().url(server.url("/v1/me")).build()).execute()

    @Test
    fun `attaches bearer token to the api host`() {
        server.dispatcher = acceptOnly("old")
        val response = client(FakeTokenStore(valid), successRefresher()).get()

        assertEquals(200, response.code)
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `never attaches the token to other hosts`() {
        server.enqueue(MockResponse())
        client(FakeTokenStore(valid), successRefresher(), host = "api.spotify.com").get()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `refreshes once and retries after a 401`() {
        server.dispatcher = acceptOnly("new")
        val store = FakeTokenStore(valid)

        val response = client(store, successRefresher()).get()

        assertEquals(200, response.code)
        assertEquals("new", store.currentTokens()?.accessToken)
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `concurrent 401s trigger a single refresh`() {
        server.dispatcher = acceptOnly("new")
        val client = client(FakeTokenStore(valid), successRefresher())
        val pool = Executors.newFixedThreadPool(6)

        val codes = (1..6).map { pool.submit<Int> { client.get().use { it.code } } }.map { it.get(10, TimeUnit.SECONDS) }

        pool.shutdown()
        assertTrue(codes.all { it == 200 })
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `expired tokens are refreshed proactively`() {
        server.dispatcher = acceptOnly("new")
        val store = FakeTokenStore(valid.copy(expiresAtEpochMs = clock.millis() - 1))

        client(store, successRefresher()).get()

        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `invalid grant signs the user out and flags the session as expired`() {
        server.dispatcher = acceptOnly("never")
        val store = FakeTokenStore(valid)

        val response = client(store, { RefreshOutcome.InvalidGrant }).get()

        assertEquals(401, response.code)
        assertNull(store.currentTokens())
        assertTrue(store.sessionExpired.value)
    }

    @Test
    fun `transient refresh failure keeps tokens`() {
        server.dispatcher = acceptOnly("never")
        val store = FakeTokenStore(valid)

        client(store, { RefreshOutcome.TransientFailure }).get()

        assertEquals(valid, store.currentTokens())
    }
}
