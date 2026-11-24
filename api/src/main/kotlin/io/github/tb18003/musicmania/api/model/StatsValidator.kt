/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.model

import io.github.tb18003.musicmania.api.http.ApiException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Mirrors the database constraints so clients get a precise 400 instead of a generic error.
 * The database still enforces every rule (defence in depth).
 */
object StatsValidator {
    const val MAX_PERIODS = 8
    const val MAX_TOP_ITEMS = 10
    private const val MAX_COUNT = 10_000_000
    private const val MAX_STREAK_DAYS = 36_600
    private const val MAX_NAME_LENGTH = 200
    private val SPOTIFY_ITEM_ID = Regex("^[A-Za-z0-9]{1,64}$")

    fun validate(upload: StatsUpload) {
        val problems = buildList {
            if (upload.periods.isEmpty()) add("periods must not be empty")
            if (upload.periods.size > MAX_PERIODS) add("at most $MAX_PERIODS periods per upload")
            if (upload.periods.map { it.kind to it.start }.toSet().size != upload.periods.size) add("duplicate periods")
            upload.periods.forEachIndexed { index, period -> addAll(problemsOf(period).map { "periods[$index]: $it" }) }
        }
        if (problems.isNotEmpty()) throw ApiException.BadRequest("invalid_stats", problems.joinToString("; "))
    }

    private fun problemsOf(period: PeriodStats): List<String> = startProblems(period) + countProblems(period) + topItemProblems(period)

    private fun startProblems(period: PeriodStats): List<String> {
        val start = parseDate(period.start)
        return when {
            start == null -> listOf("start must be an ISO date (yyyy-MM-dd)")
            !period.kind.isAligned(start) -> listOf("start ${period.start} is not the first day of a ${period.kind.dbValue}")
            else -> emptyList()
        }
    }

    private fun countProblems(period: PeriodStats): List<String> = buildList {
        if (period.minutes !in 0..period.kind.maxMinutes) add("minutes must be between 0 and ${period.kind.maxMinutes}")
        listOf(
            "streams" to period.streams,
            "uniqueArtists" to period.uniqueArtists,
            "uniqueTracks" to period.uniqueTracks,
        ).forEach { (name, value) -> if (value !in 0..MAX_COUNT) add("$name must be between 0 and $MAX_COUNT") }
        if (period.currentStreak !in 0..MAX_STREAK_DAYS || period.longestStreak !in 0..MAX_STREAK_DAYS) add("streaks out of range")
        if (period.currentStreak > period.longestStreak) add("currentStreak cannot exceed longestStreak")
    }

    private fun topItemProblems(period: PeriodStats): List<String> = buildList {
        mapOf("topArtists" to period.topArtists, "topTracks" to period.topTracks).forEach { (name, items) ->
            if (items.size > MAX_TOP_ITEMS) add("$name holds at most $MAX_TOP_ITEMS items")
            items.forEachIndexed { i, item ->
                if (item.name.isBlank() || item.name.length > MAX_NAME_LENGTH) add("$name[$i].name must be 1-$MAX_NAME_LENGTH characters")
                if (item.spotifyId != null && !SPOTIFY_ITEM_ID.matches(item.spotifyId)) add("$name[$i].spotifyId is not a Spotify id")
            }
        }
    }

    fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
