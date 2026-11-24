/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.UserRepository
import io.github.tb18003.musicmania.core.domain.usecase.GetListeningOverviewUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopArtistsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetTopTracksUseCase
import io.github.tb18003.musicmania.core.domain.usecase.ListeningOverview
import io.github.tb18003.musicmania.core.domain.usecase.ObserveNowPlayingUseCase
import io.github.tb18003.musicmania.core.domain.usecase.SyncListeningDataUseCase
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.model.Ranked
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val overview: ListeningOverview? = null,
    val topArtists: List<Ranked<Artist>> = emptyList(),
    val topTracks: List<Ranked<Track>> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    getOverview: GetListeningOverviewUseCase,
    getTopArtists: GetTopArtistsUseCase,
    getTopTracks: GetTopTracksUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val syncListeningData: SyncListeningDataUseCase,
) : ViewModel() {
    private val refreshState = MutableStateFlow(RefreshState())

    val uiState: StateFlow<HomeUiState> = combine(
        userRepository.profile,
        getOverview(TimeRange.FOUR_WEEKS),
        getTopArtists(TimeRange.FOUR_WEEKS).map { it.take(PREVIEW_ARTISTS) }.onStart { emit(emptyList()) },
        getTopTracks(TimeRange.FOUR_WEEKS).map { it.take(PREVIEW_TRACKS) }.onStart { emit(emptyList()) },
        refreshState,
    ) { profile, overview, artists, tracks, refresh ->
        HomeUiState(
            isLoading = false,
            profile = profile,
            overview = overview,
            topArtists = artists,
            topTracks = tracks,
            isRefreshing = refresh.isRefreshing,
            error = refresh.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /** Polled only while the screen is visible (WhileSubscribed), saving API quota. */
    val nowPlaying: StateFlow<NowPlaying?> = observeNowPlaying()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        refresh(userInitiated = false)
    }

    fun refresh(userInitiated: Boolean = true) {
        if (refreshState.value.isRefreshing) return
        viewModelScope.launch {
            refreshState.update { it.copy(isRefreshing = userInitiated, error = null) }
            userRepository.refresh()
            val result = syncListeningData()
            refreshState.update { RefreshState(isRefreshing = false, error = (result as? DataResult.Failure)?.error) }
        }
    }

    fun dismissError() = refreshState.update { it.copy(error = null) }

    private data class RefreshState(
        val isRefreshing: Boolean = false,
        val error: AppError? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PREVIEW_ARTISTS = 10
        const val PREVIEW_TRACKS = 5
    }
}
