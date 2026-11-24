/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.tb18003.musicmania.core.domain.usecase.ObserveAlbumDetailUseCase
import io.github.tb18003.musicmania.core.domain.usecase.ObserveArtistDetailUseCase
import io.github.tb18003.musicmania.core.domain.usecase.ObserveTrackDetailUseCase
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakeCatalogRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val history = InMemoryHistoryRepository()

    private fun viewModel(vararg args: Pair<String, Any?>) = DetailViewModel(
        SavedStateHandle(mapOf(*args)),
        ObserveTrackDetailUseCase(catalog, history),
        ObserveArtistDetailUseCase(catalog, history),
        ObserveAlbumDetailUseCase(catalog, history),
    )

    @Test
    fun `artist detail merges metadata and local stats`() = runTest {
        catalog.artists["a1"] = TestData.artist("a1", name = "Band", genres = listOf("indie"))
        history.setEvents(List(4) { TestData.play(track = "S$it", artist = "Band") })

        viewModel("kind" to "artist", "id" to "a1", "name" to "Band").uiState.test {
            val state = expectMostRecentItem()
            assertEquals(DetailKind.ARTIST, state.kind)
            assertEquals(listOf("indie"), state.genres)
            assertEquals(4, state.stats?.streams)
            assertEquals("spotify:artist:a1", state.spotifyUri)
            assertEquals("", state.sharedKeyArtist)
        }
    }

    @Test
    fun `imported tracks without id still show stats`() = runTest {
        history.setEvents(List(2) { TestData.play(track = "Song", artist = "Band") })

        viewModel("kind" to "track", "name" to "Song", "artist" to "Band").uiState.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.stats?.streams)
            assertEquals("Band", state.subtitle)
            assertEquals(null, state.spotifyUri)
        }
    }
}
