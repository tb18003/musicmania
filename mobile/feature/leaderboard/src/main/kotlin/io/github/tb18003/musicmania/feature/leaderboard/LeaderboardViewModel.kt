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

data class ExpandedFriend(
    val spotifyUserId: String,
    val stats: FriendStats? = null,
)

data class LeaderboardUiState(
    val metric: LeaderboardMetric = LeaderboardMetric.MINUTES,
    val period: LeaderboardPeriod = LeaderboardPeriod.WEEK,
    val content: LeaderboardContent = LeaderboardContent.Loading,
    val expanded: ExpandedFriend? = null,
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

    init {
        load()
    }

    fun selectMetric(metric: LeaderboardMetric) {
        savedStateHandle[KEY_METRIC] = metric
        state.update { it.copy(metric = metric, expanded = null) }
        load()
    }

    fun selectPeriod(period: LeaderboardPeriod) {
        savedStateHandle[KEY_PERIOD] = period
        state.update { it.copy(period = period, expanded = null) }
        load()
    }

    fun retry() = load()

    /** Opt in, upload my stats right away so I appear immediately, then show the ranking. */
    fun enableSharing() {
        viewModelScope.launch {
            state.update { it.copy(isEnablingSharing = true) }
            when (val result = cloud.setSharing(true)) {
                is DataResult.Success -> publishStats()
                is DataResult.Failure -> state.update { it.copy(content = contentFor(result.error)) }
            }
            state.update { it.copy(isEnablingSharing = false) }
            load()
        }
    }

    fun toggleFriend(spotifyUserId: String) {
        if (state.value.expanded?.spotifyUserId == spotifyUserId) {
            state.update { it.copy(expanded = null) }
            return
        }
        state.update { it.copy(expanded = ExpandedFriend(spotifyUserId)) }
        viewModelScope.launch {
            val stats = (getFriendStats(spotifyUserId, state.value.period) as? DataResult.Success)?.data
            state.update { current ->
                if (current.expanded?.spotifyUserId ==
                    spotifyUserId
                ) {
                    current.copy(expanded = ExpandedFriend(spotifyUserId, stats))
                } else {
                    current
                }
            }
        }
    }

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
        return when (val result = getLeaderboard(current.metric, current.period)) {
            is DataResult.Success -> LeaderboardContent.Ranking(result.data)
            is DataResult.Failure -> contentFor(result.error)
        }
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
