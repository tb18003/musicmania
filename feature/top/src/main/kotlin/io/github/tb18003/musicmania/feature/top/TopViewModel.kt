/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.top

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.usecase.CreateTopTracksPlaylistUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopAlbumsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopArtistsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopGenresUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopTracksUseCase
import io.github.tb18003.musicmania.core.domain.usecase.RefreshTopItemsUseCase
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.Genre
import io.github.tb18003.musicmania.core.model.Ranked
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopAlbum
import io.github.tb18003.musicmania.core.model.Track
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TopTab { TRACKS, ARTISTS, ALBUMS, GENRES }

sealed interface TopContent {
    data object Loading : TopContent

    data class Tracks(val items: List<Ranked<Track>>) : TopContent

    data class Artists(val items: List<Ranked<Artist>>) : TopContent

    data class Albums(val items: List<Ranked<TopAlbum>>) : TopContent

    data class Genres(val items: List<Genre>) : TopContent

    val isEmpty: Boolean
        get() = when (this) {
            Loading -> false
            is Tracks -> items.isEmpty()
            is Artists -> items.isEmpty()
            is Albums -> items.isEmpty()
            is Genres -> items.isEmpty()
        }
}

data class TopUiState(
    val range: TimeRange = TimeRange.FOUR_WEEKS,
    val tab: TopTab = TopTab.TRACKS,
    val content: TopContent = TopContent.Loading,
    val isRefreshing: Boolean = false,
    val canCreatePlaylist: Boolean = false,
)

sealed interface TopEvent {
    data class PlaylistCreated(val uri: String) : TopEvent

    data class Error(val error: AppError) : TopEvent
}

@HiltViewModel
class TopViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getTopTracks: GetTopTracksUseCase,
    private val getTopArtists: GetTopArtistsUseCase,
    private val getTopAlbums: GetTopAlbumsUseCase,
    private val getTopGenres: GetTopGenresUseCase,
    private val refreshTopItems: RefreshTopItemsUseCase,
    private val createTopPlaylist: CreateTopTracksPlaylistUseCase,
) : ViewModel() {
    // Survives process death so the user returns to the same tab/range.
    private val range = savedStateHandle.getStateFlow(KEY_RANGE, TimeRange.FOUR_WEEKS)
    private val tab = savedStateHandle.getStateFlow(KEY_TAB, TopTab.TRACKS)
    private val refreshing = MutableStateFlow(false)
    private val refreshedRanges = mutableSetOf<TimeRange>()
    private val events = Channel<TopEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TopEvent> = events.receiveAsFlow()

    val uiState: StateFlow<TopUiState> = combine(range, tab) { r, t -> r to t }
        .flatMapLatest { (r, t) ->
            content(r, t).map { TopUiState(range = r, tab = t, content = it, canCreatePlaylist = t == TopTab.TRACKS) }
        }
        .combine(refreshing) { state, isRefreshing -> state.copy(isRefreshing = isRefreshing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TopUiState())

    init {
        refreshIfNeeded(range.value)
    }

    private fun content(range: TimeRange, tab: TopTab): Flow<TopContent> = when (tab) {
        TopTab.TRACKS -> getTopTracks(range).map { TopContent.Tracks(it) }
        TopTab.ARTISTS -> getTopArtists(range).map { TopContent.Artists(it) }
        TopTab.ALBUMS -> getTopAlbums(range).map { TopContent.Albums(it) }
        TopTab.GENRES -> getTopGenres(range).map { TopContent.Genres(it) }
    }.onStart { emit(TopContent.Loading) }

    fun selectRange(newRange: TimeRange) {
        savedStateHandle[KEY_RANGE] = newRange
        refreshIfNeeded(newRange)
    }

    fun selectTab(newTab: TopTab) {
        savedStateHandle[KEY_TAB] = newTab
    }

    fun refresh() = launchRefresh(range.value)

    /** Each API-backed range is refreshed at most once per screen session. */
    private fun refreshIfNeeded(target: TimeRange) {
        if (target.isApiBacked && refreshedRanges.add(target)) launchRefresh(target)
    }

    private fun launchRefresh(target: TimeRange) {
        viewModelScope.launch {
            refreshing.value = true
            val result = refreshTopItems(target)
            refreshing.value = false
            if (result is DataResult.Failure) events.send(TopEvent.Error(result.error))
        }
    }

    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            when (val result = createTopPlaylist(range.value, name, description)) {
                is DataResult.Success -> events.send(TopEvent.PlaylistCreated(result.data))
                is DataResult.Failure -> events.send(TopEvent.Error(result.error))
            }
        }
    }

    private companion object {
        const val KEY_RANGE = "range"
        const val KEY_TAB = "tab"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
