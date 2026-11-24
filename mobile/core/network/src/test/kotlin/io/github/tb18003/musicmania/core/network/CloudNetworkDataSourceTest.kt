/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network

import io.github.tb18003.musicmania.core.common.config.CloudConfig
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.datastore.token.AuthTokens
import io.github.tb18003.musicmania.core.network.auth.AllowedApiHost
import io.github.tb18003.musicmania.core.network.auth.AuthInterceptor
import io.github.tb18003.musicmania.core.network.auth.FakeTokenStore
import io.github.tb18003.musicmania.core.network.auth.RefreshOutcome
import io.github.tb18003.musicmania.core.network.auth.TokenAuthenticator
import io.github.tb18003.musicmania.core.network.auth.TokenRefreshCoordinator
import io.github.tb18003.musicmania.core.network.auth.TokenRefresher
import io.github.tb18003.musicmania.core.network.di.cloudAllowedHost
import io.github.tb18003.musicmania.core.network.model.CloudPeriodStatsDto
import io.github.tb18003.musicmania.core.network.model.CloudStatsUploadDto
import io.github.tb18003.musicmania.core.network.model.CloudTopItemDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudNetworkDataSourceTest {
    private val server = MockWebServer()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun dataSource(refreshTo: String = "fresh"): CloudNetworkDataSource {
        val store = FakeTokenStore(AuthTokens("old", "refresh", expiresAtEpochMs = clock.millis() + 3_600_000))
        val refresher = TokenRefresher { current -> RefreshOutcome.Success(current.copy(accessToken = refreshTo)) }
        val coordinator = TokenRefreshCoordinator(store, refresher, clock)
        val host = AllowedApiHost(server.hostName)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(coordinator, host))
            .authenticator(TokenAuthenticator(coordinator, host))
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MusicManiaCloudApi::class.java)
        return RetrofitCloudNetworkDataSource(api)
    }

    @Test
    fun `the token only goes to the configured API host, and nowhere when disabled`() {
        val enabled = cloudAllowedHost(CloudConfig("https://musicmania-api.example.run.app"))
        assertTrue(enabled.matches("https://musicmania-api.example.run.app/v1/me".toHttpUrl()))
        assertFalse(enabled.matches("https://api.spotify.com/v1/me".toHttpUrl()))

        val disabled = cloudAllowedHost(CloudConfig.Disabled)
        assertFalse(disabled.matches(CloudConfig.DISABLED_BASE_URL.toHttpUrl()))
        assertFalse(disabled.matches("https://evil.example/v1/me".toHttpUrl()))
    }

    @Test
    fun `a 403 from the API means not on the allowlist`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"not_allowlisted"}"""))
        assertEquals(DataResult.Failure(AppError.NotAllowlisted), dataSource().me())
    }

    @Test
    fun `an expired Spotify token is refreshed once and the call retried`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.getHeader("Authorization") == "Bearer fresh") {
                MockResponse().setBody("""{"spotifyUserId":"ana","displayName":"Ana","role":"member","sharingEnabled":true}""")
            } else {
                MockResponse().setResponseCode(401)
            }
        }
        val me = dataSource(refreshTo = "fresh").me()
        assertTrue(me is DataResult.Success && me.data.sharingEnabled)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `uploads use the field names of the API contract`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val upload = CloudStatsUploadDto(
            listOf(
                CloudPeriodStatsDto(
                    kind = "week",
                    start = "2026-11-23",
                    minutes = 300,
                    streams = 90,
                    uniqueArtists = 20,
                    uniqueTracks = 70,
                    currentStreak = 3,
                    longestStreak = 10,
                    topArtists = listOf(CloudTopItemDto("Nova Lights")),
                ),
            ),
        )

        assertEquals(DataResult.Success(Unit), dataSource().uploadStats(upload))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/me/stats", request.path)
        val period = json.parseToJsonElement(request.body.readUtf8()).jsonObject["periods"]!!.jsonArray.single().jsonObject
        // Every field the API requires is present, and nothing it does not know about is sent.
        val required = setOf("kind", "start", "minutes", "streams", "uniqueArtists", "uniqueTracks")
        val allowed = required + setOf("currentStreak", "longestStreak", "topArtists", "topTracks")
        assertTrue(period.keys.containsAll(required), "missing: ${required - period.keys}")
        assertTrue(allowed.containsAll(period.keys), "unknown: ${period.keys - allowed}")
    }
}
