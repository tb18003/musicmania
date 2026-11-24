/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.routes

import io.github.tb18003.musicmania.api.auth.ApiUser
import io.github.tb18003.musicmania.api.auth.apiUser
import io.github.tb18003.musicmania.api.db.Identity
import io.github.tb18003.musicmania.api.db.LeaderboardRepository
import io.github.tb18003.musicmania.api.db.ProfileRepository
import io.github.tb18003.musicmania.api.db.StatsRepository
import io.github.tb18003.musicmania.api.http.ApiException
import io.github.tb18003.musicmania.api.model.LeaderboardResponse
import io.github.tb18003.musicmania.api.model.MeResponse
import io.github.tb18003.musicmania.api.model.Metric
import io.github.tb18003.musicmania.api.model.PeriodKind
import io.github.tb18003.musicmania.api.model.SettingsRequest
import io.github.tb18003.musicmania.api.model.StatsUpload
import io.github.tb18003.musicmania.api.model.StatsValidator
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.utils.io.readBuffer
import kotlinx.io.readString
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate

class Repositories(
    val profiles: ProfileRepository,
    val stats: StatsRepository,
    val leaderboard: LeaderboardRepository,
)

private val ApiUser.identity get() = Identity(spotifyUserId, displayName, avatarUrl)

/** Authenticated `/v1` endpoints. The caller's id always comes from the verified token. */
fun Route.v1Routes(repos: Repositories, json: Json, clock: Clock) {
    get("/me") {
        val user = call.apiUser
        val profile = repos.profiles.upsert(user.identity)
        call.respond(MeResponse(profile.spotifyUserId, profile.displayName, user.role.name.lowercase(), profile.sharingEnabled))
    }

    put("/me/settings") {
        val user = call.apiUser
        val request = call.receiveLimited(json, SettingsRequest.serializer())
        val profile = repos.profiles.setSharing(user.identity, request.sharingEnabled)
        call.respond(MeResponse(profile.spotifyUserId, profile.displayName, user.role.name.lowercase(), profile.sharingEnabled))
    }

    put("/me/stats") {
        val user = call.apiUser
        val upload = call.receiveLimited(json, StatsUpload.serializer())
        StatsValidator.validate(upload)
        repos.stats.replace(user.identity, upload.periods)
        call.respond(HttpStatusCode.NoContent)
    }

    delete("/me") {
        repos.profiles.delete(call.apiUser.spotifyUserId)
        call.respond(HttpStatusCode.NoContent)
    }

    get("/leaderboards") {
        val metric = Metric.fromQuery(call.request.queryParameters["metric"] ?: Metric.MINUTES.apiValue)
            ?: throw ApiException.BadRequest("invalid_metric", "metric must be one of ${Metric.entries.joinToString { it.apiValue }}")
        val (kind, start) = call.period(clock)
        val entries = repos.leaderboard.leaderboard(metric, kind, start, me = call.apiUser.spotifyUserId)
        call.respond(LeaderboardResponse(metric.apiValue, kind, start.toString(), entries))
    }

    get("/users/{spotifyUserId}/stats") {
        val target = call.parameters["spotifyUserId"].orEmpty()
        val (kind, start) = call.period(clock)
        val stats = repos.leaderboard.userStats(target, requester = call.apiUser.spotifyUserId, kind = kind, start = start)
            ?: throw ApiException.NotFound("stats_not_found", "No shared stats for this user and period.")
        call.respond(stats)
    }
}

/** `period` (default week) and optional `start` (default: the current period, in UTC). */
private fun ApplicationCall.period(clock: Clock): Pair<PeriodKind, LocalDate> {
    val kind = PeriodKind.fromQuery(request.queryParameters["period"] ?: PeriodKind.WEEK.dbValue)
        ?: throw ApiException.BadRequest("invalid_period", "period must be one of ${PeriodKind.entries.joinToString { it.dbValue }}")
    val start = request.queryParameters["start"]?.let { raw ->
        StatsValidator.parseDate(raw)?.takeIf(kind::isAligned)
            ?: throw ApiException.BadRequest("invalid_start", "start must be the first day of the ${kind.dbValue} (yyyy-MM-dd)")
    } ?: kind.currentStart(LocalDate.now(clock))
    return kind to start
}

const val MAX_BODY_BYTES = 64 * 1024

/** Reads and decodes a JSON body, refusing anything larger than [MAX_BODY_BYTES]. */
private suspend fun <T> ApplicationCall.receiveLimited(json: Json, deserializer: DeserializationStrategy<T>): T =
    json.decodeOrBadRequest(deserializer, receiveBodyText())

/** The raw body, refusing anything larger than [MAX_BODY_BYTES] (declared or actual size). */
private suspend fun ApplicationCall.receiveBodyText(): String {
    val declared = request.contentLength() ?: 0
    val body = if (declared > MAX_BODY_BYTES) null else receiveChannel().readBuffer(MAX_BODY_BYTES + 1L).readString()
    return body?.takeIf { it.length <= MAX_BODY_BYTES } ?: throw ApiException.PayloadTooLarge(MAX_BODY_BYTES)
}

private fun <T> Json.decodeOrBadRequest(deserializer: DeserializationStrategy<T>, body: String): T {
    val decoded = try {
        decodeFromString(deserializer, body)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
    return decoded ?: throw ApiException.BadRequest("invalid_json", "The request body is not valid JSON for this endpoint.")
}
