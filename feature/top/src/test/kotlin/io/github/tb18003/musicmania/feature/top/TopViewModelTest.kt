/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.top

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.usecase.CreateTopTracksPlaylistUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopAlbumsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopArtistsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopGenresUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopTracksUseCase
import io.github.tb18003.musicmania.core.domain.usecase.RefreshTopItemsUseCase
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopList
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakePlaylistRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeTopItemsRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TopViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val topItems = FakeTopItemsRepository()
    private val history = InMemoryHistoryRepository()
    private val playlists = FakePlaylistRepository()
    private val clock = TestData.fixedClock

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = TopViewModel(
        savedStateHandle = handle,
        getTopTracks = GetTopTracksUseCase(topItems, history, clock),
        getTopArtists = GetTopArtistsUseCase(topItems, history, clock),
        getTopAlbums = GetTopAlbumsUseCase(topItems, history, clock),
        getTopGenres = GetTopGenresUseCase(topItems),
        refreshTopItems = RefreshTopItemsUseCase(topItems),
        createTopPlaylist = CreateTopTracksPlaylistUseCase(topItems, playlists),
    )

    @Test
    fun `switching tabs and ranges updates content and refreshes each range once`() = runTest {
        topItems.setTracks(TimeRange.FOUR_WEEKS, TopList(listOf(TestData.track("1")), null, null))
        topItems.setArtists(TimeRange.SIX_MONTHS, TopList(listOf(TestData.artist("a", genres = listOf("pop"))), null, null))
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertIs<TopContent.Tracks>(expectMostRecentItem().content)
            viewModel.selectRange(TimeRange.SIX_MONTHS)
            viewModel.selectTab(TopTab.GENRES)
            val genres = expectMostRecentItem().content
            assertIs<TopContent.Genres>(genres)
            assertEquals("pop", genres.items.single().name)
            viewModel.selectRange(TimeRange.FOUR_WEEKS)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(TimeRange.FOUR_WEEKS, TimeRange.SIX_MONTHS), topItems.refreshedRanges)
    }

    @Test
    fun `selection is restored from saved state`() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("range" to TimeRange.LIFETIME, "tab" to TopTab.ALBUMS)))
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(TimeRange.LIFETIME, state.range)
            assertEquals(TopTab.ALBUMS, state.tab)
        }
        assertEquals(emptyList(), topItems.refreshedRanges)
    }

    @Test
    fun `playlist creation and errors are delivered as events`() = runTest {
        topItems.setTracks(TimeRange.FOUR_WEEKS, TopList(listOf(TestData.track("1")), null, null))
        val viewModel = viewModel()

        viewModel.eventFlow.test {
            viewModel.createPlaylist("n", "d")
            assertEquals(TopEvent.PlaylistCreated("spotify:playlist:new"), awaitItem())
            playlists.result = DataResult.Failure(AppError.Forbidden)
            viewModel.createPlaylist("n", "d")
            assertEquals(TopEvent.Error(AppError.Forbidden), awaitItem())
        }
    }
}
