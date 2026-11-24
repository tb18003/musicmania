/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.auth

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpotifyTokenVerifierTest {
    private val spotify = MockWebServer()
    private lateinit var verifier: SpotifyTokenVerifier

    @BeforeEach
    fun setUp() {
        spotify.start()
        verifier = SpotifyTokenVerifier(spotify.url("/").toString().trimEnd('/'), TokenCache())
    }

    @AfterEach
    fun tearDown() = spotify.close()

    private fun respond(code: Int, body: String = "", retryAfter: String? = null) {
        val builder = MockResponse.Builder().code(code).body(body)
        retryAfter?.let { builder.addHeader("Retry-After", it) }
        spotify.enqueue(builder.build())
    }

    @Test
    fun `a token Spotify accepts identifies the user and is cached`() = runBlocking {
        respond(200, """{"id":"ana_01","display_name":"Ana","country":"ES"}""")

        val first = verifier.verify("token-ana")
        val second = verifier.verify("token-ana")

        assertEquals(TokenVerification.Valid(SpotifyUser("ana_01", "Ana")), first)
        assertEquals(first, second)
        assertEquals(1, spotify.requestCount, "the second call is served from the cache")
        assertEquals("Bearer token-ana", spotify.takeRequest().headers["Authorization"])
    }

    @Test
    fun `the sharpest https profile picture is kept`() = runBlocking {
        respond(
            200,
            """{"id":"ana_01","images":[{"url":"https://i.scdn.co/image/small","height":64,"width":64},
               {"url":"https://i.scdn.co/image/large","height":300,"width":300},
               {"url":"http://insecure.example/huge","height":1000,"width":1000}]}""",
        )
        assertEquals(
            TokenVerification.Valid(SpotifyUser("ana_01", null, "https://i.scdn.co/image/large")),
            verifier.verify("token-ana"),
        )
    }

    @Test
    fun `users without a picture have no avatar`() = runBlocking {
        respond(200, """{"id":"ana_01","images":[]}""")
        assertEquals(TokenVerification.Valid(SpotifyUser("ana_01", null, null)), verifier.verify("token-ana"))
    }

    @Test
    fun `rejected and forbidden tokens are invalid`() = runBlocking {
        respond(401)
        respond(403)
        assertEquals(TokenVerification.Invalid, verifier.verify("expired"))
        assertEquals(TokenVerification.Invalid, verifier.verify("not-on-the-spotify-app"))
    }

    @Test
    fun `rate limits and outages are unavailable, with Retry-After when given`() = runBlocking {
        respond(429, retryAfter = "7")
        respond(503)
        assertEquals(TokenVerification.Unavailable(7), verifier.verify("t1"))
        assertEquals(TokenVerification.Unavailable(null), verifier.verify("t2"))
    }

    @Test
    fun `garbage never reaches Spotify`() = runBlocking {
        assertEquals(TokenVerification.Invalid, verifier.verify(""))
        assertEquals(TokenVerification.Invalid, verifier.verify("abc\r\nX-Injected: 1"))
        assertEquals(TokenVerification.Invalid, verifier.verify("x".repeat(5000)))
        assertEquals(0, spotify.requestCount)
    }

    @Test
    fun `unexpected profile ids are rejected`() = runBlocking {
        respond(200, """{"id":"../../etc/passwd"}""")
        assertEquals(TokenVerification.Invalid, verifier.verify("weird"))
    }
}
