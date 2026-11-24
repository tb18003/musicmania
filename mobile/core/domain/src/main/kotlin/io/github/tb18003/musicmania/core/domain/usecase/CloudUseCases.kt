/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.CloudRepository
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.repository.SessionRepository
import io.github.tb18003.musicmania.core.domain.stats.StreakCalculator
import io.github.tb18003.musicmania.core.domain.stats.toDateRange
import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.SharedPeriodStats
import io.github.tb18003.musicmania.core.model.TimeRange
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds the aggregates published to the leaderboard from the local history: one entry per
 * calendar period (local time zone), within the same bounds the API enforces.
 */
class BuildSharedStatsUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): List<SharedPeriodStats> {
        val today = LocalDate.now(clock)
        val activeDays = history.observeDaily(TimeRange.LIFETIME.toDateRange(clock)).first().map { it.date }
        val streaks = StreakCalculator.compute(activeDays, today)
        return LeaderboardPeriod.entries.map { period ->
            val start = period.startOf(today)
            val range = rangeOf(period, start)
            val summary = history.observeSummary(range).first()
            SharedPeriodStats(
                period = period,
                start = start,
                minutes = summary.minutes.coerceAtMost(period.maxMinutes),
                streams = summary.streams,
                uniqueArtists = summary.uniqueArtists,
                uniqueTracks = summary.uniqueTracks,
                currentStreak = streaks.current,
                longestStreak = streaks.longest,
                topArtists = history.observeTopArtists(range, TOP_ITEMS).first().map { it.artistName.take(MAX_NAME) },
                topTracks = history.observeTopTracks(range, TOP_ITEMS).first().map { it.trackName.take(MAX_NAME) },
            )
        }
    }

    private fun rangeOf(period: LeaderboardPeriod, start: LocalDate): DateRange {
        val end = period.endExclusive(start)?.let { it.atStartOfDay(clock.zone).toInstant() }
            ?: clock.instant().plus(Duration.ofMinutes(1))
        return DateRange(start.atStartOfDay(clock.zone).toInstant(), end)
    }

    private companion object {
        const val TOP_ITEMS = 10
        const val MAX_NAME = 200
        private const val MINUTES_PER_DAY = 24L * 60

        /** The same caps as the API: a period cannot hold more minutes than it has. */
        val LeaderboardPeriod.maxMinutes: Long
            get() = when (this) {
                LeaderboardPeriod.WEEK -> 7 * MINUTES_PER_DAY
                LeaderboardPeriod.MONTH -> 31 * MINUTES_PER_DAY
                LeaderboardPeriod.YEAR -> 366 * MINUTES_PER_DAY
                LeaderboardPeriod.ALL_TIME -> 100 * 366 * MINUTES_PER_DAY
            }
    }
}

/**
 * Called after every background sync: publishes the aggregates, but only for a real Spotify
 * session (never demo data) and only if the user opted in to sharing.
 * @return true when something was uploaded.
 */
class PublishListeningStatsUseCase @Inject constructor(
    private val cloud: CloudRepository,
    private val session: SessionRepository,
    private val buildSharedStats: BuildSharedStatsUseCase,
) {
    suspend operator fun invoke(): DataResult<Boolean> {
        if (!cloud.isEnabled || !isSignedIn()) return DataResult.Success(false)
        val profile = when (val me = cloud.me()) {
            is DataResult.Success -> me.data
            is DataResult.Failure -> return me
        }
        if (!profile.sharingEnabled) return DataResult.Success(false)
        return when (val upload = cloud.publish(buildSharedStats())) {
            is DataResult.Success -> DataResult.Success(true)
            is DataResult.Failure -> upload
        }
    }

    /** The session flow starts with Loading: wait for the real state, never decide on the placeholder. */
    private suspend fun isSignedIn(): Boolean = session.session.first { it != SessionState.Loading } == SessionState.SignedIn
}

class GetLeaderboardUseCase @Inject constructor(
    private val cloud: CloudRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(metric: LeaderboardMetric, period: LeaderboardPeriod): DataResult<List<LeaderboardEntry>> =
        cloud.leaderboard(metric, period, period.startOf(LocalDate.now(clock)))
}

class GetFriendStatsUseCase @Inject constructor(
    private val cloud: CloudRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(spotifyUserId: String, period: LeaderboardPeriod): DataResult<FriendStats> =
        cloud.friendStats(spotifyUserId, period, period.startOf(LocalDate.now(clock)))
}
