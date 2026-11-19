/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.stats

import io.github.tb18003.musicmania.core.model.ListeningPersonality
import io.github.tb18003.musicmania.core.model.ListeningSummary
import io.github.tb18003.musicmania.core.model.MilestoneType
import io.github.tb18003.musicmania.core.model.RankMovement
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.testing.TestData
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RankMovementCalculatorTest {
    @Test
    fun `without previous snapshot every movement is unknown`() {
        val result = RankMovementCalculator.compute(listOf("a", "b"), previousRanks = null)
        assertEquals(listOf(RankMovement.Unknown, RankMovement.Unknown), result)
    }

    @Test
    fun `computes up, down, same and new`() {
        val previous = mapOf("a" to 3, "b" to 1, "c" to 2)
        val result = RankMovementCalculator.compute(listOf("a", "c", "b", "d"), previous)
        assertEquals(
            listOf(RankMovement.Up(2), RankMovement.Same, RankMovement.Down(2), RankMovement.New),
            result,
        )
    }
}

class TopDerivationsTest {
    @Test
    fun `albums are ranked by Borda score of their tracks`() {
        val tracks = listOf(
            TestData.track(id = "1", albumId = "x"),
            TestData.track(id = "2", albumId = "y"),
            TestData.track(id = "3", albumId = "y"),
        )
        val albums = TopDerivations.albumsFromTracks(tracks)
        // x = 3 points, y = 2 + 1 = 3 points -> tie keeps first-seen order; both present
        assertEquals(setOf("x", "y"), albums.map { it.album.id }.toSet())
        assertEquals(3.0, albums.first().score)
    }

    @Test
    fun `genres are weighted by artist rank and normalised`() {
        val artists = listOf(
            TestData.artist(id = "1", genres = listOf("indie", "pop")),
            TestData.artist(id = "2", genres = listOf("pop")),
            TestData.artist(id = "3", genres = emptyList()),
        )
        val genres = TopDerivations.genresFromArtists(artists)
        assertEquals("pop", genres.first().name)
        assertEquals(1.0, genres.first().score)
        assertEquals(2, genres.first().artistCount)
        assertEquals(0.6, genres.first { it.name == "indie" }.score, 1e-9)
    }

    @Test
    fun `no genres yields empty list`() {
        assertTrue(TopDerivations.genresFromArtists(listOf(TestData.artist())).isEmpty())
    }
}

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 6, 15)

    @Test
    fun `empty history has no streak`() {
        val streaks = StreakCalculator.compute(emptyList(), today)
        assertEquals(0, streaks.current)
        assertEquals(0, streaks.longest)
    }

    @Test
    fun `current streak survives until the end of the next day`() {
        val days = (1L..5L).map { today.minusDays(it) } // yesterday and 4 days before
        val streaks = StreakCalculator.compute(days, today)
        assertEquals(5, streaks.current)
        assertEquals(5, streaks.longest)
    }

    @Test
    fun `gap breaks the current streak but keeps the longest`() {
        val old = (10L..19L).map { today.minusDays(it) } // 10-day streak in the past
        val recent = listOf(today, today.minusDays(1))
        val streaks = StreakCalculator.compute(old + recent + recent, today)
        assertEquals(2, streaks.current)
        assertEquals(10, streaks.longest)
        assertEquals(today.minusDays(10), streaks.longestEndedOn)
    }

    @Test
    fun `stale streak is not current`() {
        val streaks = StreakCalculator.compute(listOf(today.minusDays(3)), today)
        assertEquals(0, streaks.current)
        assertEquals(1, streaks.longest)
    }
}

class MilestoneCalculatorTest {
    @Test
    fun `milestones reflect lifetime totals`() {
        val summary = ListeningSummary(listenedMs = 12_000L * 60_000, streams = 900, uniqueTracks = 10, uniqueArtists = 60)
        val milestones = MilestoneCalculator.compute(summary, longestStreak = 8)

        val minutes = milestones.filter { it.type == MilestoneType.MINUTES }
        assertTrue(minutes.first { it.threshold == 10_000L }.achieved)
        assertTrue(!minutes.first { it.threshold == 50_000L }.achieved)

        val next = MilestoneCalculator.nextGoals(milestones).associateBy { it.type }
        assertEquals(50_000L, next.getValue(MilestoneType.MINUTES).threshold)
        assertEquals(1_000L, next.getValue(MilestoneType.STREAMS).threshold)
        assertEquals(0.9f, next.getValue(MilestoneType.STREAMS).progress)
        assertEquals(30L, next.getValue(MilestoneType.STREAK_DAYS).threshold)
    }
}

class TimeRangesTest {
    @Test
    fun `api ranges map to a window ending now`() {
        val range = TimeRange.FOUR_WEEKS.toDateRange(TestData.fixedClock)
        assertTrue(range.endExclusive.isAfter(TestData.NOW))
        assertEquals(28L * 24 * 60 * 60 * 1000, range.lengthMs)
    }

    @Test
    fun `lifetime starts at epoch`() {
        assertEquals(Instant.EPOCH, TimeRange.LIFETIME.toDateRange(TestData.fixedClock).start)
    }

    @Test
    fun `personality follows peak hour`() {
        assertEquals(ListeningPersonality.NIGHT_OWL, ListeningPersonality.fromPeakHour(2))
        assertEquals(ListeningPersonality.EARLY_BIRD, ListeningPersonality.fromPeakHour(7))
        assertEquals(ListeningPersonality.EVENING_DRIFTER, ListeningPersonality.fromPeakHour(20))
    }
}
