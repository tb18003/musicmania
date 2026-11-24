/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api

import io.github.tb18003.musicmania.api.config.AppConfig
import io.github.tb18003.musicmania.api.config.DatabaseConfig
import io.github.tb18003.musicmania.api.db.Database
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The whole API against the real schema (database/supabase/migrations) on an embedded Postgres 17,
 * connected as the least-privileged `musicmania_api` role, with a fake Spotify.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {
    private lateinit var postgres: EmbeddedPostgres
    private lateinit var database: Database
    private val spotify = MockWebServer()

    /** Wednesday: the current week starts on Monday 2026-11-23. */
    private val clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC)
    private val week = "2026-11-23"

    @BeforeAll
    fun startInfrastructure() {
        postgres = EmbeddedPostgres.builder().start()
        applyMigrations()
        admin("alter role musicmania_api with password 'test-password'")
        database = Database(DatabaseConfig("jdbc:postgresql://localhost:${postgres.port}/postgres", "musicmania_api", "test-password"))

        spotify.dispatcher = FakeSpotify
        spotify.start()
    }

    @AfterAll
    fun stopInfrastructure() {
        database.close()
        spotify.close()
        postgres.close()
    }

    @BeforeEach
    fun resetData() {
        admin("delete from musicmania.allowlist")
        admin(
            """
            insert into musicmania.allowlist (spotify_user_id, email, display_name, enabled) values
              ('ana', 'ana@example.com', 'Ana', true),
              ('bruno', 'bruno@example.com', 'Bruno', true),
              ('dora', 'dora@example.com', 'Dora', false)
            """.trimIndent(),
        )
    }

    private fun applyMigrations() {
        val dir = File(requireNotNull(System.getProperty("musicmania.migrations")) { "musicmania.migrations not set" })
        dir.listFiles { file -> file.extension == "sql" }.orEmpty().sortedBy { it.name }.forEach { admin(it.readText()) }
    }

    /** Runs SQL as the database owner (what a human admin would do in the SQL editor). */
    private fun admin(sql: String) {
        postgres.postgresDatabase.connection.use { c -> c.createStatement().use { it.execute(sql) } }
    }

    private fun api(test: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        val config = AppConfig(
            port = 0,
            database = DatabaseConfig("unused", "unused", "unused"),
            spotifyApiBaseUrl = spotify.url("/").toString().trimEnd('/'),
            corsOrigins = emptyList(),
        )
        application { musicManiaApi(Dependencies.create(config, database, clock)) }
        test(client)
    }

    private suspend fun HttpClient.putJson(path: String, token: String, body: String) = put(path) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun stats(minutes: Int, streams: Int = 100) =
        """{"periods":[{"kind":"week","start":"$week","minutes":$minutes,"streams":$streams,"uniqueArtists":10,"uniqueTracks":40,
           "currentStreak":2,"longestStreak":5,"topArtists":[{"name":"Nova Lights"}]}]}"""

    // ---------------------------------------------------------------- authentication

    @Test
    fun `health is public, everything else needs a valid token`() = api { client ->
        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        val missing = client.get("/v1/me")
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("application/problem+json", missing.headers["Content-Type"]?.substringBefore(';'))
        assertEquals("missing_token", missing.json()["code"]?.jsonPrimitive?.content)

        val invalid = client.get("/v1/me") { bearerAuth("token-expired") }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
        assertEquals("invalid_token", invalid.json()["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `only enabled allowlist members get in`() = api { client ->
        val stranger = client.get("/v1/me") { bearerAuth("token-stranger") }
        assertEquals(HttpStatusCode.Forbidden, stranger.status)
        assertEquals("not_allowlisted", stranger.json()["code"]?.jsonPrimitive?.content)

        assertEquals(HttpStatusCode.Forbidden, client.get("/v1/me") { bearerAuth("token-dora") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/v1/me") { bearerAuth("token-ana") }.status)
    }

    @Test
    fun `a Spotify outage is a 503 with Retry-After, not a 401`() = api { client ->
        val response = client.get("/v1/me") { bearerAuth("token-ratelimited") }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("7", response.headers["Retry-After"])
    }

    // ---------------------------------------------------------------- profile, stats, leaderboard

    @Test
    fun `first call creates a private profile`() = api { client ->
        val me = client.get("/v1/me") { bearerAuth("token-ana") }.json()
        assertEquals("ana", me["spotifyUserId"]?.jsonPrimitive?.content)
        assertEquals("member", me["role"]?.jsonPrimitive?.content)
        assertEquals("false", me["sharingEnabled"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the leaderboard only ranks people who share`() = api { client ->
        assertEquals(HttpStatusCode.NoContent, client.putJson("/v1/me/stats", "token-ana", stats(minutes = 300)).status)
        assertEquals(HttpStatusCode.NoContent, client.putJson("/v1/me/stats", "token-bruno", stats(minutes = 500)).status)

        // Nobody shares yet.
        val empty = client.get("/v1/leaderboards?metric=minutes&period=week") { bearerAuth("token-ana") }.json()
        assertEquals(0, empty["entries"]?.jsonArray?.size)

        client.putJson("/v1/me/settings", "token-ana", """{"sharingEnabled":true}""")
        client.putJson("/v1/me/settings", "token-bruno", """{"sharingEnabled":true}""")

        val board = client.get("/v1/leaderboards?metric=minutes&period=week&start=$week") { bearerAuth("token-ana") }.json()
        val entries = requireNotNull(board["entries"]?.jsonArray).map { it.jsonObject }
        assertEquals(listOf("bruno", "ana"), entries.map { it["spotifyUserId"]?.jsonPrimitive?.content })
        assertEquals(listOf(1, 2), entries.map { it["rank"]?.jsonPrimitive?.int })
        assertEquals(listOf("false", "true"), entries.map { it["isMe"]?.jsonPrimitive?.content })
    }

    @Test
    fun `the leaderboard shows each person's Spotify picture`() = api { client ->
        client.putJson("/v1/me/stats", "token-ana", stats(minutes = 300))
        client.putJson("/v1/me/stats", "token-bruno", stats(minutes = 500))
        client.putJson("/v1/me/settings", "token-ana", """{"sharingEnabled":true}""")
        client.putJson("/v1/me/settings", "token-bruno", """{"sharingEnabled":true}""")

        val entries = client.get("/v1/leaderboards?start=$week") { bearerAuth("token-ana") }.json()["entries"]!!.jsonArray
        val avatars = entries.associate { it.jsonObject["spotifyUserId"]?.jsonPrimitive?.content to it.jsonObject["avatarUrl"] }
        assertEquals("https://i.scdn.co/image/ana", avatars["ana"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, avatars["bruno"] ?: JsonNull, "no picture on Spotify, none here")
    }

    @Test
    fun `uploading again replaces the period instead of adding to it`() = api { client ->
        client.putJson("/v1/me/stats", "token-ana", stats(minutes = 300))
        client.putJson("/v1/me/stats", "token-ana", stats(minutes = 320))
        client.putJson("/v1/me/settings", "token-ana", """{"sharingEnabled":true}""")
        val entry = client.get("/v1/leaderboards") { bearerAuth("token-ana") }.json()["entries"]!!.jsonArray.single().jsonObject
        assertEquals(320, entry["value"]?.jsonPrimitive?.int)
    }

    @Test
    fun `private stats are visible to their owner only`() = api { client ->
        client.putJson("/v1/me/stats", "token-bruno", stats(minutes = 500))

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/users/bruno/stats?period=week") { bearerAuth("token-ana") }.status)
        val own = client.get("/v1/users/bruno/stats?period=week") { bearerAuth("token-bruno") }
        assertEquals(HttpStatusCode.OK, own.status)
        assertEquals("Nova Lights", own.json()["topArtists"]!!.jsonArray.single().jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `deleting my data removes it from the rankings but keeps me allowed`() = api { client ->
        client.putJson("/v1/me/stats", "token-ana", stats(minutes = 300))
        client.putJson("/v1/me/settings", "token-ana", """{"sharingEnabled":true}""")

        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me") { bearerAuth("token-ana") }.status)

        val board = client.get("/v1/leaderboards") { bearerAuth("token-ana") }.json()
        assertEquals(0, board["entries"]?.jsonArray?.size)
        val me = client.get("/v1/me") { bearerAuth("token-ana") }.json()
        assertEquals("false", me["sharingEnabled"]?.jsonPrimitive?.content, "a fresh, private profile")
    }

    // ---------------------------------------------------------------- input validation

    @Test
    fun `bad input is rejected with precise problems`() = api { client ->
        val misaligned = client.putJson("/v1/me/stats", "token-ana", stats(minutes = 300).replace(week, "2026-11-24"))
        assertEquals(HttpStatusCode.BadRequest, misaligned.status)
        assertEquals("invalid_stats", misaligned.json()["code"]?.jsonPrimitive?.content)

        val notJson = client.putJson("/v1/me/stats", "token-ana", "{oops")
        assertEquals("invalid_json", notJson.json()["code"]?.jsonPrimitive?.content)

        val tooBig = client.putJson("/v1/me/stats", "token-ana", """{"periods":[]}""" + " ".repeat(70_000))
        assertEquals(HttpStatusCode.PayloadTooLarge, tooBig.status)

        val metric = client.get("/v1/leaderboards?metric=followers") { bearerAuth("token-ana") }
        assertEquals("invalid_metric", metric.json()["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `tokens are verified with Spotify once and then cached`() = api { client ->
        val before = spotify.requestCount
        repeat(3) { client.get("/v1/me") { bearerAuth("token-bruno") } }
        assertTrue(spotify.requestCount - before <= 1, "at most one Spotify call for three API calls")
    }

    /** Maps test tokens to Spotify answers. */
    private object FakeSpotify : Dispatcher() {
        private val users = mapOf("token-ana" to "ana", "token-bruno" to "bruno", "token-dora" to "dora", "token-stranger" to "stranger")

        override fun dispatch(request: RecordedRequest): MockResponse {
            val token = request.headers["Authorization"]?.removePrefix("Bearer ")
            return when {
                token == "token-ratelimited" -> MockResponse.Builder().code(429).addHeader("Retry-After", "7").build()
                token == "token-ana" -> ok(
                    """{"id":"ana","display_name":null,"images":[{"url":"https://i.scdn.co/image/ana","height":300}]}""",
                )
                token in users -> ok("""{"id":"${users[token]}","display_name":null}""")
                else -> MockResponse.Builder().code(401).build()
            }
        }

        private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
    }
}
