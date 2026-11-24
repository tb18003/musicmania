/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** What the friends leaderboard ranks by. */
enum class LeaderboardMetric { MINUTES, STREAMS, STREAK, UNIQUE_ARTISTS }

/** Calendar periods: everyone is ranked on exactly the same window (local calendar). */
enum class LeaderboardPeriod {
    WEEK,
    MONTH,
    YEAR,
    ALL_TIME,
    ;

    /** First day of the period that contains [today] (weeks start on Monday). */
    fun startOf(today: LocalDate): LocalDate = when (this) {
        WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> today.withDayOfMonth(1)
        YEAR -> today.withDayOfYear(1)
        ALL_TIME -> ALL_TIME_START
    }

    /** First day after the period that starts on [start]; null for all time. */
    fun endExclusive(start: LocalDate): LocalDate? = when (this) {
        WEEK -> start.plusWeeks(1)
        MONTH -> start.plusMonths(1)
        YEAR -> start.plusYears(1)
        ALL_TIME -> null
    }

    companion object {
        val ALL_TIME_START: LocalDate = LocalDate.of(1970, 1, 1)
    }
}

data class LeaderboardEntry(
    val rank: Int,
    val spotifyUserId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val value: Long,
    val isMe: Boolean,
)

/** My profile on the cloud API. */
data class CloudProfile(
    val spotifyUserId: String,
    val displayName: String?,
    val sharingEnabled: Boolean,
)

/** Aggregated statistics of one period, the only listening data that ever leaves the device. */
data class SharedPeriodStats(
    val period: LeaderboardPeriod,
    val start: LocalDate,
    val minutes: Long,
    val streams: Int,
    val uniqueArtists: Int,
    val uniqueTracks: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val topArtists: List<String>,
    val topTracks: List<String>,
)

/** A friend's statistics for one period, shown when their leaderboard row is expanded. */
data class FriendStats(
    val spotifyUserId: String,
    val displayName: String?,
    val minutes: Long,
    val streams: Int,
    val uniqueArtists: Int,
    val currentStreak: Int,
    val topArtists: List<String>,
    val topTracks: List<String>,
)
