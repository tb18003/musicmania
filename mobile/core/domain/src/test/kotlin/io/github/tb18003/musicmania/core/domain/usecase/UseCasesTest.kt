/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import app.cash.turbine.test
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.ListeningPersonality
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.model.RankMovement
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopList
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakePlaybackRepository
import io.github.tb18003.musicmania.core.testing.repository.FakePlaylistRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeTopItemsRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class GetTopTracksUseCaseTest {
    private val topItems = FakeTopItemsRepository()
    private val history = InMemoryHistoryRepository()
    private val useCase = GetTopTracksUseCase(topItems, history, TestData.fixedClock)

    @Test
    fun `api range uses top snapshot with movements`() = runTest {
        topItems.setTracks(
            TimeRange.FOUR_WEEKS,
            TopList(listOf(TestData.track("b"), TestData.track("a")), previousRanks = mapOf("a" to 1), fetchedAt = TestData.NOW),
        )

        val ranked = useCase(TimeRange.FOUR_WEEKS).first()

        assertEquals(listOf("b", "a"), ranked.map { it.item.id })
        assertEquals(RankMovement.New, ranked[0].movement)
        assertEquals(RankMovement.Down(1), ranked[1].movement)
    }

    @Test
    fun `lifetime range is computed from local history`() = runTest {
        history.setEvents(
            listOf(
                TestData.play(track = "X", playedAt = TestData.NOW.minus(Duration.ofDays(900))),
                TestData.play(track = "Y"),
                TestData.play(track = "Y"),
            ),
        )

        val ranked = useCase(TimeRange.LIFETIME).first()

        assertEquals(listOf("Y", "X"), ranked.map { it.item.name })
        assertEquals(2, ranked.first().streams)
    }
}

class ObserveNowPlayingUseCaseTest {
    @Test
    fun `emits playing track and null after failures`() = runTest {
        val playback = FakePlaybackRepository()
        val nowPlaying = NowPlaying(TestData.track(), progressMs = 1_000, isPlaying = true, fetchedAt = TestData.NOW)
        playback.responses += DataResult.Success(nowPlaying)
        playback.responses += DataResult.Failure(AppError.Network)
        val useCase = ObserveNowPlayingUseCase(playback, interval = 1.seconds, maxBackoff = 4.seconds)

        useCase().test {
            assertEquals(nowPlaying, awaitItem())
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class BuildRecapUseCaseTest {
    @Test
    fun `recap aggregates tops, personality and new artists`() = runTest {
        val history = InMemoryHistoryRepository()
        val night = TestData.NOW.minus(Duration.ofHours(10)) // 02:00 UTC
        history.setEvents(
            listOf(
                TestData.play(track = "A", artist = "Old", playedAt = TestData.NOW.minus(Duration.ofDays(400))),
                TestData.play(track = "B", artist = "New", playedAt = night),
                TestData.play(track = "B", artist = "New", playedAt = night),
                TestData.play(track = "C", artist = "Old", playedAt = night),
            ),
        )
        val range = DateRange(TestData.NOW.minus(Duration.ofDays(7)), TestData.NOW.plusSeconds(1))

        val recap = BuildRecapUseCase(history, TestData.fixedClock)(range).first()

        assertEquals(3, recap.summary.streams)
        assertEquals("New", recap.topArtists.first().artistName)
        assertEquals("B", recap.topTracks.first().trackName)
        assertEquals(ListeningPersonality.NIGHT_OWL, recap.personality)
        assertEquals(1, recap.newArtists)
    }
}

class CreateTopTracksPlaylistUseCaseTest {
    @Test
    fun `creates playlist with top track uris`() = runTest {
        val topItems = FakeTopItemsRepository().apply {
            setTracks(TimeRange.SIX_MONTHS, TopList(listOf(TestData.track("1"), TestData.track("2")), null, null))
        }
        val playlists = FakePlaylistRepository()

        val result = CreateTopTracksPlaylistUseCase(topItems, playlists)(TimeRange.SIX_MONTHS, "n", "d")

        assertIs<DataResult.Success<String>>(result)
        assertEquals(listOf("spotify:track:1", "spotify:track:2"), playlists.lastUris)
    }

    @Test
    fun `fails when there are no tracks`() = runTest {
        val topItems = FakeTopItemsRepository().apply { setTracks(TimeRange.ONE_YEAR, TopList(emptyList(), null, null)) }

        val result = CreateTopTracksPlaylistUseCase(topItems, FakePlaylistRepository())(TimeRange.LIFETIME, "n", "d")

        assertIs<DataResult.Failure>(result)
    }
}

class SyncListeningDataUseCaseTest {
    @Test
    fun `sync refreshes all api ranges after recently played`() = runTest {
        val history = InMemoryHistoryRepository().apply { syncResult = DataResult.Success(7) }
        val topItems = FakeTopItemsRepository()

        val result = SyncListeningDataUseCase(history, RefreshTopItemsUseCase(topItems))()

        assertEquals(DataResult.Success(7), result)
        assertEquals(TimeRange.entries.filter { it.isApiBacked }, topItems.refreshedRanges)
    }

    @Test
    fun `sync stops when recently played fails`() = runTest {
        val history = InMemoryHistoryRepository().apply { syncResult = DataResult.Failure(AppError.Unauthorized) }
        val topItems = FakeTopItemsRepository()

        val result = SyncListeningDataUseCase(history, RefreshTopItemsUseCase(topItems))()

        assertEquals(DataResult.Failure(AppError.Unauthorized), result)
        assertEquals(emptyList(), topItems.refreshedRanges)
    }
}
