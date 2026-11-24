/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.demo

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.model.CloudProfile
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import javax.inject.Inject
import javax.inject.Singleton

/** Fictional friends so demo mode can show the leaderboard without any server. */
@Singleton
internal class DemoLeaderboard @Inject constructor() {
    @Volatile
    private var sharing = true

    private data class Friend(
        val id: String,
        val name: String,
        val minutes: Long,
        val streams: Int,
        val streak: Int,
        val artists: Int,
        val topArtists: List<String>,
        val topTracks: List<String>,
    )

    private val friends = listOf(
        Friend(
            "demo_you",
            DEMO_NAME,
            1_240,
            410,
            12,
            58,
            listOf("Nova Lights", "Velvet Static", "Paper Moons"),
            listOf("Neon Rain", "Afterglow"),
        ),
        Friend("demo_ana", "Ana", 1_510, 520, 30, 33, listOf("Coral Echo", "Nova Lights"), listOf("Low Tide", "Neon Rain")),
        Friend("demo_bruno", "Bruno", 980, 350, 5, 71, listOf("Glass Harbor", "Saturn Kids"), listOf("Static Hearts", "Orbit")),
        Friend("demo_carla", "Carla", 1_240, 300, 9, 44, listOf("Paper Moons", "Coral Echo"), listOf("Afterglow", "Sunday Drive")),
    )

    fun profile(): DataResult<CloudProfile> = DataResult.Success(CloudProfile(ME, DEMO_NAME, sharing))

    fun setSharing(enabled: Boolean): DataResult<CloudProfile> {
        sharing = enabled
        return profile()
    }

    /** Competition ranking (1, 1, 3), like the real API. */
    fun leaderboard(metric: LeaderboardMetric): DataResult<List<LeaderboardEntry>> {
        val visible = friends.filter { it.id != ME || sharing }
        val sorted = visible.sortedByDescending { it.value(metric) }
        return DataResult.Success(
            sorted.map { friend ->
                LeaderboardEntry(
                    rank = 1 + sorted.count { it.value(metric) > friend.value(metric) },
                    spotifyUserId = friend.id,
                    displayName = friend.name,
                    avatarUrl = null,
                    value = friend.value(metric),
                    isMe = friend.id == ME,
                )
            },
        )
    }

    fun friendStats(id: String): DataResult<FriendStats> {
        val friend = friends.firstOrNull { it.id == id } ?: return DataResult.Failure(AppError.NotFound)
        return DataResult.Success(
            FriendStats(
                friend.id,
                friend.name,
                friend.minutes,
                friend.streams,
                friend.artists,
                friend.streak,
                friend.topArtists,
                friend.topTracks,
            ),
        )
    }

    private fun Friend.value(metric: LeaderboardMetric): Long = when (metric) {
        LeaderboardMetric.MINUTES -> minutes
        LeaderboardMetric.STREAMS -> streams.toLong()
        LeaderboardMetric.STREAK -> streak.toLong()
        LeaderboardMetric.UNIQUE_ARTISTS -> artists.toLong()
    }

    private companion object {
        const val ME = "demo_you"

        /** Not "You": the row already carries a "You" label. */
        const val DEMO_NAME = "Demo"
    }
}
