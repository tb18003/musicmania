/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.repository

import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.model.RankedItem
import io.github.tb18003.musicmania.core.model.SharedPeriodStats
import io.github.tb18003.musicmania.core.network.model.CloudTopItemDto
import io.github.tb18003.musicmania.core.network.model.CloudUserStatsDto
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class CloudMappersTest {
    @Test
    fun `uploads send HTTPS covers only`() {
        val stats = SharedPeriodStats(
            period = LeaderboardPeriod.WEEK,
            start = LocalDate.parse("2026-11-23"),
            minutes = 1,
            streams = 1,
            uniqueArtists = 1,
            uniqueTracks = 1,
            currentStreak = 0,
            longestStreak = 0,
            topArtists = listOf(RankedItem("Lit", "https://i.scdn.co/image/lit")),
            topTracks = listOf(RankedItem("Plain", "http://example.com/plain")),
        )

        val dto = stats.toDto()

        assertEquals(listOf(CloudTopItemDto("Lit", imageUrl = "https://i.scdn.co/image/lit")), dto.topArtists)
        assertEquals(listOf(CloudTopItemDto("Plain")), dto.topTracks)
    }

    @Test
    fun `friends' covers are kept only when they are HTTPS`() {
        val dto = CloudUserStatsDto(
            spotifyUserId = "ana",
            minutes = 1,
            streams = 1,
            uniqueArtists = 1,
            uniqueTracks = 1,
            currentStreak = 0,
            longestStreak = 0,
            topArtists = listOf(CloudTopItemDto("Lit", imageUrl = "https://i.scdn.co/image/lit"), CloudTopItemDto("None")),
            topTracks = listOf(CloudTopItemDto("Bad", imageUrl = "javascript:alert(1)")),
        )

        val stats = dto.toModel()

        assertEquals(listOf(RankedItem("Lit", "https://i.scdn.co/image/lit"), RankedItem("None")), stats.topArtists)
        assertEquals(listOf(RankedItem("Bad")), stats.topTracks)
    }
}
