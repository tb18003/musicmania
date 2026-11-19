/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.recap

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.tb18003.musicmania.core.domain.usecase.BuildRecapUseCase
import io.github.tb18003.musicmania.core.model.ListeningClock
import io.github.tb18003.musicmania.core.model.ListeningSummary
import io.github.tb18003.musicmania.core.model.Recap
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val history = InMemoryHistoryRepository()
    private fun viewModel() = RecapViewModel(SavedStateHandle(), BuildRecapUseCase(history, TestData.fixedClock), TestData.fixedClock)

    @Test
    fun `periods map to calendar ranges`() {
        val vm = viewModel()
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), vm.rangeFor(RecapPeriod.THIS_YEAR).start)
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), vm.rangeFor(RecapPeriod.LAST_YEAR).start)
        assertEquals(Instant.parse("2026-05-01T00:00:00Z"), vm.rangeFor(RecapPeriod.LAST_MONTH).start)
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), vm.rangeFor(RecapPeriod.LAST_MONTH).endExclusive)
    }

    @Test
    fun `selecting a period rebuilds the recap`() = runTest {
        history.setEvents(listOf(TestData.play(playedAt = TestData.NOW.minus(Duration.ofDays(30)))))
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(1, expectMostRecentItem().recap?.summary?.streams)
            vm.selectPeriod(RecapPeriod.THIS_MONTH)
            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 6), state.periodLabelMonth)
            assertTrue(state.recap?.isEmpty == true)
        }
    }

    @Test
    fun `story pages adapt to available data`() {
        val empty = Recap(
            range = viewModel().rangeFor(RecapPeriod.THIS_YEAR),
            summary = ListeningSummary.Empty,
            topArtists = emptyList(),
            topTracks = emptyList(),
            topAlbum = null,
            clock = ListeningClock.Empty,
            personality = null,
            longestStreak = 0,
            newArtists = 0,
        )
        assertEquals(listOf(StoryPageType.INTRO, StoryPageType.MINUTES, StoryPageType.DISCOVERIES, StoryPageType.SHARE), buildPages(empty))
    }
}
