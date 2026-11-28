/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.repository

import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.model.CloudProfile
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LatestRelease
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.model.SharedPeriodStats
import java.time.LocalDate

/**
 * The optional MusicMania cloud API (friends leaderboard). Authenticates with the user's
 * Spotify token. When the API URL is not configured, [isEnabled] is false and the feature is hidden.
 */
interface CloudRepository {
    val isEnabled: Boolean

    /** The latest release announced by the API and its notes, signed in, signed out or in demo mode. */
    suspend fun latestRelease(): DataResult<LatestRelease>

    suspend fun me(): DataResult<CloudProfile>

    /** Opt in or out of appearing in the rankings (off by default). */
    suspend fun setSharing(enabled: Boolean): DataResult<CloudProfile>

    /** Uploads aggregates only; never individual plays. */
    suspend fun publish(stats: List<SharedPeriodStats>): DataResult<Unit>

    suspend fun leaderboard(metric: LeaderboardMetric, period: LeaderboardPeriod, start: LocalDate): DataResult<List<LeaderboardEntry>>

    suspend fun friendStats(spotifyUserId: String, period: LeaderboardPeriod, start: LocalDate): DataResult<FriendStats>

    /** Deletes my profile and statistics from the server. */
    suspend fun deleteMyData(): DataResult<Unit>
}
