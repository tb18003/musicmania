/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.model.RankedItem
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakeCloudRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSessionRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudUseCasesTest {
    // TestData.NOW is Monday 2026-06-15 12:00 UTC.
    private val history = InMemoryHistoryRepository().apply {
        setEvents(
            listOf(
                TestData.play(track = "Neon Rain", artist = "Nova Lights", playedAt = TestData.NOW, msPlayed = 180_000),
                TestData.play(
                    track = "Low Tide",
                    artist = "Coral Echo",
                    playedAt = Instant.parse("2026-06-10T09:00:00Z"),
                    msPlayed = 180_000,
                ),
                TestData.play(
                    track = "Old Song",
                    artist = "Paper Moons",
                    playedAt = Instant.parse("2025-12-31T22:00:00Z"),
                    msPlayed = 180_000,
                ),
            ),
        )
    }
    private val build = BuildSharedStatsUseCase(history, TestData.fixedClock)
    private val cloud = FakeCloudRepository()

    private fun publish(session: SessionState = SessionState.SignedIn) =
        PublishListeningStatsUseCase(cloud, FakeSessionRepository(session), build)

    @Test
    fun `aggregates are built per calendar period from the local history`() = runTest {
        val stats = build().associateBy { it.period }

        assertEquals(LocalDate.parse("2026-06-15"), stats.getValue(LeaderboardPeriod.WEEK).start)
        assertEquals(LocalDate.parse("2026-06-01"), stats.getValue(LeaderboardPeriod.MONTH).start)
        assertEquals(LocalDate.parse("2026-01-01"), stats.getValue(LeaderboardPeriod.YEAR).start)
        assertEquals(LeaderboardPeriod.ALL_TIME_START, stats.getValue(LeaderboardPeriod.ALL_TIME).start)

        assertEquals(listOf(3L, 6L, 6L, 9L), LeaderboardPeriod.entries.map { stats.getValue(it).minutes })
        assertEquals(listOf("Nova Lights"), stats.getValue(LeaderboardPeriod.WEEK).topArtists.map { it.name })
        assertEquals(3, stats.getValue(LeaderboardPeriod.ALL_TIME).topTracks.size)
        assertTrue(stats.values.all { it.currentStreak <= it.longestStreak })
    }

    @Test
    fun `top items carry their HTTPS cover, and anything else is dropped`() = runTest {
        val covers = InMemoryHistoryRepository().apply {
            setEvents(
                listOf(
                    TestData.play(track = "Lit", artist = "A", playedAt = TestData.NOW, imageUrl = "https://i.scdn.co/image/lit"),
                    TestData.play(track = "Plain", artist = "B", playedAt = TestData.NOW, imageUrl = "http://example.com/plain"),
                ),
            )
        }
        val week = BuildSharedStatsUseCase(covers, TestData.fixedClock)().first { it.period == LeaderboardPeriod.WEEK }

        assertEquals(
            setOf(RankedItem("Lit", "https://i.scdn.co/image/lit"), RankedItem("Plain", null)),
            week.topTracks.toSet(),
        )
        assertEquals("https://i.scdn.co/image/lit", week.topArtists.first { it.name == "A" }.imageUrl)
    }

    @Test
    fun `cover URLs must be HTTPS and at most 2048 characters`() {
        assertEquals("https://i.scdn.co/image/x", RankedItem.safeImageUrl("https://i.scdn.co/image/x"))
        assertNull(RankedItem.safeImageUrl("http://i.scdn.co/image/x"))
        assertNull(RankedItem.safeImageUrl("javascript:alert(1)"))
        assertNull(RankedItem.safeImageUrl("https://"))
        assertNull(RankedItem.safeImageUrl("https://" + "a".repeat(2041)))
        assertNull(RankedItem.safeImageUrl(null))
    }

    @Test
    fun `nothing is published unless the user shares from a real session`() = runTest {
        assertEquals(DataResult.Success(false), publish().invoke(), "sharing is off by default")
        assertEquals(DataResult.Success(false), publish(SessionState.Demo).invoke(), "demo data never leaves the device")

        cloud.isEnabled = false
        cloud.profile = cloud.profile.copy(sharingEnabled = true)
        assertEquals(DataResult.Success(false), publish().invoke(), "no API configured")
        assertTrue(cloud.published.isEmpty())
    }

    @Test
    fun `when sharing, every period is uploaded`() = runTest {
        cloud.profile = cloud.profile.copy(sharingEnabled = true)

        assertEquals(DataResult.Success(true), publish().invoke())
        assertEquals(LeaderboardPeriod.entries, cloud.published.single().map { it.period })
    }

    @Test
    fun `a user who is not on the allowlist gets a typed error`() = runTest {
        cloud.failure = AppError.NotAllowlisted
        assertEquals(DataResult.Failure(AppError.NotAllowlisted), publish().invoke())
    }

    @Test
    fun `the leaderboard always asks for the current period`() = runTest {
        GetLeaderboardUseCase(cloud, TestData.fixedClock)(LeaderboardMetric.STREAMS, LeaderboardPeriod.MONTH)
        assertEquals(
            Triple(LeaderboardMetric.STREAMS, LeaderboardPeriod.MONTH, LocalDate.parse("2026-06-01")),
            cloud.leaderboardRequests.single(),
        )
    }
}
