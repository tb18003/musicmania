/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.home

import app.cash.turbine.test
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.usecase.GetListeningOverviewUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopArtistsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopTracksUseCase
import io.github.tb18003.musicmania.core.domain.usecase.ObserveNowPlayingUseCase
import io.github.tb18003.musicmania.core.domain.usecase.RefreshTopItemsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.SyncListeningDataUseCase
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopList
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakePlaybackRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeTopItemsRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeUserRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val history = InMemoryHistoryRepository()
    private val topItems = FakeTopItemsRepository()

    private fun viewModel() = HomeViewModel(
        userRepository = FakeUserRepository(),
        getOverview = GetListeningOverviewUseCase(history, TestData.fixedClock),
        getTopArtists = GetTopArtistsUseCase(topItems, history, TestData.fixedClock),
        getTopTracks = GetTopTracksUseCase(topItems, history, TestData.fixedClock),
        observeNowPlaying = ObserveNowPlayingUseCase(FakePlaybackRepository(), 1.seconds, 1.seconds),
        syncListeningData = SyncListeningDataUseCase(history, RefreshTopItemsUseCase(topItems)),
    )

    @Test
    fun `state combines profile, overview and previews`() = runTest {
        history.setEvents(List(3) { TestData.play(track = "Song $it") })
        topItems.setArtists(TimeRange.FOUR_WEEKS, TopList(List(12) { TestData.artist("a$it") }, null, null))
        topItems.setTracks(TimeRange.FOUR_WEEKS, TopList(List(8) { TestData.track("t$it") }, null, null))

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals("Test User", state.profile?.displayName)
            assertEquals(3, state.overview?.comparison?.current?.streams)
            assertEquals(10, state.topArtists.size)
            assertEquals(5, state.topTracks.size)
        }
    }

    @Test
    fun `refresh failures are exposed once and can be dismissed`() = runTest {
        topItems.refreshResult = DataResult.Failure(AppError.RateLimited(30))
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(AppError.RateLimited(30), expectMostRecentItem().error)
            viewModel.dismissError()
            assertEquals(null, awaitItem().error)
        }
    }
}
