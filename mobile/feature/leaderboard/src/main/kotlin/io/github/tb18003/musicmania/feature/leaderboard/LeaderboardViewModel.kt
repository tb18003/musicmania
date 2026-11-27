/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.leaderboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.CloudRepository
import io.github.tb18003.musicmania.core.domain.repository.UserRepository
import io.github.tb18003.musicmania.core.domain.usecase.GetFriendStatsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetLeaderboardUseCase
import io.github.tb18003.musicmania.core.domain.usecase.PublishListeningStatsUseCase
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LeaderboardContent {
    data object Loading : LeaderboardContent

    /** The account is not on the server's allowlist: show its Spotify id to send to the admin. */
    data class NotAllowlisted(val spotifyUserId: String?) : LeaderboardContent

    /** On the allowlist, but not sharing yet (opt-in). */
    data object SharingOff : LeaderboardContent

    data class Ranking(val entries: List<LeaderboardEntry>) : LeaderboardContent

    data class Error(val error: AppError) : LeaderboardContent
}

/** The friend whose details dialog is open; [stats] stays null while loading or after a failure. */
data class SelectedFriend(
    val spotifyUserId: String,
    val stats: FriendStats? = null,
    val isLoading: Boolean = true,
)

data class LeaderboardUiState(
    val metric: LeaderboardMetric = LeaderboardMetric.MINUTES,
    val period: LeaderboardPeriod = LeaderboardPeriod.WEEK,
    val content: LeaderboardContent = LeaderboardContent.Loading,
    val selected: SelectedFriend? = null,
    val isEnablingSharing: Boolean = false,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val cloud: CloudRepository,
    private val userRepository: UserRepository,
    private val getLeaderboard: GetLeaderboardUseCase,
    private val getFriendStats: GetFriendStatsUseCase,
    private val publishStats: PublishListeningStatsUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(
        LeaderboardUiState(
            metric = savedStateHandle[KEY_METRIC] ?: LeaderboardMetric.MINUTES,
            period = savedStateHandle[KEY_PERIOD] ?: LeaderboardPeriod.WEEK,
        ),
    )
    val uiState: StateFlow<LeaderboardUiState> = state.asStateFlow()

    private var loadJob: Job? = null

    /** One automatic upload per screen if I share but am missing from the ranking. */
    private var triedSelfHeal = false

    init {
        load()
    }

    fun selectMetric(metric: LeaderboardMetric) {
        savedStateHandle[KEY_METRIC] = metric
        state.update { it.copy(metric = metric, selected = null) }
        load()
    }

    fun selectPeriod(period: LeaderboardPeriod) {
        savedStateHandle[KEY_PERIOD] = period
        state.update { it.copy(period = period, selected = null) }
        load()
    }

    fun retry() = load()

    /** Opt in, upload my stats right away so I appear immediately, then show the ranking. */
    fun enableSharing() {
        viewModelScope.launch {
            state.update { it.copy(isEnablingSharing = true) }
            val failure = when (val result = cloud.setSharing(true)) {
                is DataResult.Success -> (publishStats() as? DataResult.Failure)?.error
                is DataResult.Failure -> result.error
            }
            state.update { it.copy(isEnablingSharing = false) }
            // A failed upload is shown (with retry) instead of an empty, misleading ranking.
            if (failure != null) state.update { it.copy(content = contentFor(failure)) } else load()
        }
    }

    /** Opens a friend's details (a dialog) and loads their stats for the selected period. */
    fun openFriend(spotifyUserId: String) {
        state.update { it.copy(selected = SelectedFriend(spotifyUserId)) }
        viewModelScope.launch {
            val stats = (getFriendStats(spotifyUserId, state.value.period) as? DataResult.Success)?.data
            state.update { current ->
                if (current.selected?.spotifyUserId == spotifyUserId) {
                    current.copy(selected = SelectedFriend(spotifyUserId, stats, isLoading = false))
                } else {
                    current
                }
            }
        }
    }

    fun closeFriend() = state.update { it.copy(selected = null) }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state.update { it.copy(content = LeaderboardContent.Loading) }
            val content = when (val me = cloud.me()) {
                is DataResult.Failure -> contentFor(me.error)
                is DataResult.Success -> if (!me.data.sharingEnabled) LeaderboardContent.SharingOff else ranking()
            }
            state.update { it.copy(content = content) }
        }
    }

    private suspend fun ranking(): LeaderboardContent {
        val current = state.value
        val entries = when (val result = getLeaderboard(current.metric, current.period)) {
            is DataResult.Success -> result.data
            is DataResult.Failure -> return contentFor(result.error)
        }
        // I share but I'm not there (e.g. the upload never happened): upload once and fetch again.
        if (entries.none { it.isMe } && !triedSelfHeal) {
            triedSelfHeal = true
            if (publishStats() is DataResult.Success) return ranking()
        }
        return LeaderboardContent.Ranking(entries)
    }

    private suspend fun contentFor(error: AppError): LeaderboardContent = if (error == AppError.NotAllowlisted) {
        LeaderboardContent.NotAllowlisted(userRepository.profile.first()?.id)
    } else {
        LeaderboardContent.Error(error)
    }

    private companion object {
        const val KEY_METRIC = "metric"
        const val KEY_PERIOD = "period"
    }
}
