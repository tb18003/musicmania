/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Serializable
enum class PeriodKind(
    val dbValue: String,
    val maxMinutes: Int,
) {
    @SerialName("week")
    WEEK("week", 7 * 24 * 60),

    @SerialName("month")
    MONTH("month", 31 * 24 * 60),

    @SerialName("year")
    YEAR("year", 366 * 24 * 60),

    @SerialName("all_time")
    ALL_TIME("all_time", 100 * 366 * 24 * 60),
    ;

    /** Periods are calendar-aligned (same rule as the database's stat_periods_aligned_start check). */
    fun isAligned(start: LocalDate): Boolean = when (this) {
        WEEK -> start.dayOfWeek == DayOfWeek.MONDAY
        MONTH -> start.dayOfMonth == 1
        YEAR -> start.dayOfYear == 1
        ALL_TIME -> start == ALL_TIME_START
    }

    fun currentStart(today: LocalDate): LocalDate = when (this) {
        WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> today.withDayOfMonth(1)
        YEAR -> today.withDayOfYear(1)
        ALL_TIME -> ALL_TIME_START
    }

    companion object {
        val ALL_TIME_START: LocalDate = LocalDate.of(1970, 1, 1)

        fun fromQuery(value: String?): PeriodKind? = entries.firstOrNull { it.dbValue == value }
    }
}

/** Ranking metrics. Column names are fixed here and never built from user input. */
enum class Metric(
    val apiValue: String,
    val valueColumn: String,
    val rankColumn: String,
) {
    MINUTES("minutes", "minutes", "minutes_rank"),
    STREAMS("streams", "streams", "streams_rank"),
    STREAK("streak", "current_streak", "streak_rank"),
    UNIQUE_ARTISTS("unique_artists", "unique_artists", "unique_artists_rank"),
    ;

    companion object {
        fun fromQuery(value: String?): Metric? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class Role { MEMBER, ADMIN }

// ---------------------------------------------------------------- requests

@Serializable
data class SettingsRequest(val sharingEnabled: Boolean)

@Serializable
data class StatsUpload(val periods: List<PeriodStats>)

@Serializable
data class PeriodStats(
    val kind: PeriodKind,
    /** ISO date (yyyy-MM-dd) of the first day of the period. */
    val start: String,
    val minutes: Int,
    val streams: Int,
    val uniqueArtists: Int,
    val uniqueTracks: Int,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val topArtists: List<TopItem> = emptyList(),
    val topTracks: List<TopItem> = emptyList(),
)

@Serializable
data class TopItem(
    val name: String,
    val spotifyId: String? = null,
    /** Public HTTPS cover URL (Spotify CDN), shown in the friends' ranking. */
    val imageUrl: String? = null,
)

// ---------------------------------------------------------------- responses

@Serializable
data class Health(val status: String = "ok")

/** What changed in one release, by language code ("en", "es"). */
@Serializable
data class ReleaseNotes(
    val version: String,
    val notes: Map<String, List<String>>,
)

/**
 * The latest MusicMania release (the repository version, the same for the app and the web) and the notes
 * of the releases up to it, newest first, so an outdated app can list what it is missing.
 */
@Serializable
data class AppVersionResponse(
    val version: String,
    val releases: List<ReleaseNotes> = emptyList(),
)

@Serializable
data class MeResponse(
    val spotifyUserId: String,
    val displayName: String?,
    val role: String,
    val sharingEnabled: Boolean,
)

@Serializable
data class LeaderboardResponse(
    val metric: String,
    val period: PeriodKind,
    val start: String,
    val entries: List<LeaderboardEntry>,
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val spotifyUserId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val value: Int,
    val isMe: Boolean,
)

@Serializable
data class UserStatsResponse(
    val spotifyUserId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val period: PeriodKind,
    val start: String,
    val minutes: Int,
    val streams: Int,
    val uniqueArtists: Int,
    val uniqueTracks: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val topArtists: List<TopItem>,
    val topTracks: List<TopItem>,
)
