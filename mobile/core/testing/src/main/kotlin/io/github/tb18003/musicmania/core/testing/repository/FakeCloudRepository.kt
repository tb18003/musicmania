/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.testing.repository

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.CloudRepository
import io.github.tb18003.musicmania.core.model.CloudProfile
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.model.SharedPeriodStats
import java.time.LocalDate

/** In-memory cloud API: behaves like the server for one user and records what was sent. */
class FakeCloudRepository(override var isEnabled: Boolean = true) : CloudRepository {
    var profile = CloudProfile(spotifyUserId = "me", displayName = "Me", sharingEnabled = false)

    /** When set, every call fails with this error (e.g. [AppError.NotAllowlisted]). */
    var failure: AppError? = null
    var entries: List<LeaderboardEntry> = emptyList()
    var friends: Map<String, FriendStats> = emptyMap()

    val published = mutableListOf<List<SharedPeriodStats>>()
    val leaderboardRequests = mutableListOf<Triple<LeaderboardMetric, LeaderboardPeriod, LocalDate>>()
    var deleted = false
        private set

    private fun <T> respond(value: () -> T): DataResult<T> = failure?.let { DataResult.Failure(it) } ?: DataResult.Success(value())

    override suspend fun me() = respond { profile }

    override suspend fun setSharing(enabled: Boolean) = respond {
        profile = profile.copy(sharingEnabled = enabled)
        profile
    }

    override suspend fun publish(stats: List<SharedPeriodStats>) = respond { published += stats }

    override suspend fun leaderboard(metric: LeaderboardMetric, period: LeaderboardPeriod, start: LocalDate) = respond {
        leaderboardRequests += Triple(metric, period, start)
        entries
    }

    override suspend fun friendStats(spotifyUserId: String, period: LeaderboardPeriod, start: LocalDate): DataResult<FriendStats> =
        failure?.let { DataResult.Failure(it) } ?: friends[spotifyUserId]?.let { DataResult.Success(it) }
            ?: DataResult.Failure(AppError.NotFound)

    override suspend fun deleteMyData() = respond {
        deleted = true
        profile = profile.copy(sharingEnabled = false)
    }
}
