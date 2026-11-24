/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api

import io.github.tb18003.musicmania.api.auth.Authenticator
import io.github.tb18003.musicmania.api.auth.SpotifyAuthentication
import io.github.tb18003.musicmania.api.auth.SpotifyTokenVerifier
import io.github.tb18003.musicmania.api.auth.TokenCache
import io.github.tb18003.musicmania.api.config.AppConfig
import io.github.tb18003.musicmania.api.db.AllowlistRepository
import io.github.tb18003.musicmania.api.db.Database
import io.github.tb18003.musicmania.api.db.LeaderboardRepository
import io.github.tb18003.musicmania.api.db.ProfileRepository
import io.github.tb18003.musicmania.api.db.StatsRepository
import io.github.tb18003.musicmania.api.http.ApiException
import io.github.tb18003.musicmania.api.http.Problem
import io.github.tb18003.musicmania.api.model.Health
import io.github.tb18003.musicmania.api.routes.Repositories
import io.github.tb18003.musicmania.api.routes.v1Routes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.sql.SQLException
import java.time.Clock
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = AppConfig.fromEnvironment()
    val database = Database(config.database)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        monitor.subscribe(ApplicationStopped) { database.close() }
        musicManiaApi(Dependencies.create(config, database))
    }.start(wait = true)
}

/** Everything the HTTP layer needs; tests build it against an embedded Postgres and a fake Spotify. */
class Dependencies(
    val authenticator: Authenticator,
    val repositories: Repositories,
    val corsOrigins: List<String>,
    val clock: Clock = Clock.systemUTC(),
) {
    companion object {
        fun create(config: AppConfig, database: Database, clock: Clock = Clock.systemUTC()) = Dependencies(
            authenticator = Authenticator(
                SpotifyTokenVerifier(config.spotifyApiBaseUrl, TokenCache()),
                AllowlistRepository(database),
            ),
            repositories = Repositories(ProfileRepository(database), StatsRepository(database), LeaderboardRepository(database)),
            corsOrigins = config.corsOrigins,
            clock = clock,
        )
    }
}

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private val log = LoggerFactory.getLogger("io.github.tb18003.musicmania.api")
private val API_RATE_LIMIT = RateLimitName("api")
private const val REQUESTS_PER_MINUTE = 120

fun Application.musicManiaApi(deps: Dependencies) {
    install(ContentNegotiation) { json(ApiJson) }
    // Method, path and status only: headers (Authorization!) are never logged.
    install(CallLogging) {
        level = Level.INFO
        format { call -> "${call.request.local.method.value} ${call.request.path()} -> ${call.response.status()?.value}" }
    }
    if (deps.corsOrigins.isNotEmpty()) {
        install(CORS) {
            deps.corsOrigins.forEach { allowHost(it.substringAfter("://"), schemes = listOf("https")) }
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
        }
    }
    install(RateLimit) {
        register(API_RATE_LIMIT) {
            rateLimiter(limit = REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
            // Cloud Run puts the client address in X-Forwarded-For; origin handles both cases.
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
    install(StatusPages) { configureProblems() }

    routing {
        get("/health") { call.respond(Health()) }
        rateLimit(API_RATE_LIMIT) {
            route("/v1") {
                install(SpotifyAuthentication) { authenticator = deps.authenticator }
                v1Routes(deps.repositories, ApiJson, deps.clock)
            }
        }
    }
}

private fun io.ktor.server.plugins.statuspages.StatusPagesConfig.configureProblems() {
    exception<ApiException> { call, cause ->
        if (cause is ApiException.ServiceUnavailable) {
            cause.retryAfterSeconds?.let { call.response.headers.append(HttpHeaders.RetryAfter, it.toString()) }
        }
        call.respondProblem(cause.status, cause.code, cause.message)
    }
    exception<SQLException> { call, cause ->
        when (cause.sqlState?.take(2)) {
            // Integrity constraint (23) or data exception (22): the request held data the schema refuses.
            "23", "22" -> call.respondProblem(HttpStatusCode.BadRequest, "invalid_data", "The data violates a schema constraint.")
            else -> {
                log.error("Database error (SQLState {})", cause.sqlState, cause)
                call.respondProblem(HttpStatusCode.ServiceUnavailable, "database_unavailable", "The database is not available right now.")
            }
        }
    }
    exception<Exception> { call, cause ->
        log.error("Unhandled error", cause)
        call.respondProblem(HttpStatusCode.InternalServerError, "internal_error", "Something went wrong.")
    }
    status(HttpStatusCode.TooManyRequests) { call, status ->
        call.respondProblem(status, "rate_limited", "Too many requests. Slow down.")
    }
    status(HttpStatusCode.NotFound) { call, status ->
        call.respondProblem(status, "not_found", "No such endpoint.")
    }
}

private suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, code: String, detail: String) {
    respondText(
        text = ApiJson.encodeToString(
            Problem.serializer(),
            Problem(title = status.description, status = status.value, code = code, detail = detail),
        ),
        contentType = ContentType.parse("application/problem+json"),
        status = status,
    )
}
