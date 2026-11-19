/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.insights

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.tb18003.musicmania.core.domain.usecase.GetInsightsUseCase
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `builds a 7x24 heatmap and switches range`() = runTest {
        val history = InMemoryHistoryRepository().apply {
            // 2026-06-15 is a Monday; 12:00 UTC
            setEvents(listOf(TestData.play(), TestData.play(playedAt = TestData.NOW.minus(Duration.ofDays(400)))))
        }
        val viewModel = InsightsViewModel(SavedStateHandle(), GetInsightsUseCase(history, TestData.fixedClock))

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(7, state.heatmap.size)
            assertTrue(state.heatmap.all { it.size == 24 })
            assertEquals(180_000L, state.heatmap[0][12])
            viewModel.selectRange(TimeRange.LIFETIME)
            val lifetime = expectMostRecentItem()
            assertEquals(TimeRange.LIFETIME, lifetime.range)
            assertEquals(2, lifetime.insights?.comparison?.current?.streams)
        }
    }

    @Test
    fun `empty history is reported`() = runTest {
        val viewModel = InsightsViewModel(SavedStateHandle(), GetInsightsUseCase(InMemoryHistoryRepository(), TestData.fixedClock))
        viewModel.uiState.test { assertTrue(expectMostRecentItem().isEmpty) }
    }
}
