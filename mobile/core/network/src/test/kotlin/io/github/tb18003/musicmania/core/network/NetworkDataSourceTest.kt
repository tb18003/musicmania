/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.network.interceptor.RateLimitInterceptor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetworkDataSourceTest {
    private val server = MockWebServer()
    private lateinit var dataSource: RetrofitSpotifyNetworkDataSource

    @Before
    fun setUp() {
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpotifyApi::class.java)
        dataSource = RetrofitSpotifyNetworkDataSource(api)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `nothing playing returns null on 204`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(DataResult.Success(null), dataSource.getCurrentlyPlaying())
    }

    @Test
    fun `podcast episodes are not reported as tracks`() = runTest {
        server.enqueue(MockResponse().setBody("""{"item":null,"is_playing":true,"currently_playing_type":"episode"}"""))
        assertEquals(DataResult.Success(null), dataSource.getCurrentlyPlaying())
    }

    @Test
    fun `forbidden maps to allowlist error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(DataResult.Failure(AppError.Forbidden), dataSource.getMe())
    }

    @Test
    fun `rate limit exposes retry after`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        assertEquals(DataResult.Failure(AppError.RateLimited(30)), dataSource.getMe())
    }

    @Test
    fun `top tracks parse and skip local files`() = runTest {
        server.enqueue(MockResponse().setBody(javaClass.getResource("/top_tracks.json")!!.readText()))

        val result = dataSource.getTopTracks(SpotifyTimeRange.SHORT_TERM)

        assertIs<DataResult.Success<*>>(result)
        val tracks = (result as DataResult.Success).data
        assertEquals(listOf("Midnight Signal"), tracks.map { it.name })
        assertEquals("time_range=short_term&limit=50&offset=0", server.takeRequest().requestUrl?.query)
    }

    @Test
    fun `playlist items are added in chunks of 100`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"p1","uri":"spotify:playlist:p1"}"""))
        repeat(2) { server.enqueue(MockResponse().setBody("""{"snapshot_id":"s"}""")) }

        val result = dataSource.createPlaylistWithItems(
            io.github.tb18003.musicmania.core.network.model.CreatePlaylistRequest("n", "d"),
            List(150) { "spotify:track:$it" },
        )

        assertIs<DataResult.Success<*>>(result)
        assertEquals("/v1/me/playlists", server.takeRequest().path)
        assertEquals("/v1/playlists/p1/items", server.takeRequest().path)
        assertEquals(3, server.requestCount)
    }
}

class RateLimitInterceptorTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `short retry-after is absorbed`() {
        val sleeps = mutableListOf<Long>()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setBody("ok"))
        val client = OkHttpClient.Builder().addInterceptor(RateLimitInterceptor(sleeper = { sleeps += it })).build()

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(200, response.code)
        assertEquals(listOf(1_000L), sleeps)
    }

    @Test
    fun `long retry-after is surfaced immediately`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3600"))
        val client = OkHttpClient.Builder().addInterceptor(RateLimitInterceptor(sleeper = { error("must not sleep") })).build()

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(429, response.code)
        assertEquals(1, server.requestCount)
    }
}
