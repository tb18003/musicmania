/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.domain.usecase.GetInsightsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.Insights
import io.github.tb18003.musicmania.core.model.TimeRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import javax.inject.Inject

data class InsightsUiState(
    val range: TimeRange = TimeRange.SIX_MONTHS,
    val insights: Insights? = null,
    /** 7 rows (Mon..Sun) x 24 columns of listened milliseconds. */
    val heatmap: List<List<Long>> = emptyList(),
) {
    val isLoading: Boolean get() = insights == null
    val isEmpty: Boolean get() = insights != null && insights.comparison.current.streams == 0
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    getInsights: GetInsightsUseCase,
) : ViewModel() {
    private val range = savedStateHandle.getStateFlow(KEY_RANGE, TimeRange.SIX_MONTHS)

    val uiState: StateFlow<InsightsUiState> = range
        .flatMapLatest { r ->
            getInsights(r)
                .map { insights -> InsightsUiState(r, insights, heatmapGrid(insights)) }
                .onStart { emit(InsightsUiState(r)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsUiState())

    fun selectRange(newRange: TimeRange) {
        savedStateHandle[KEY_RANGE] = newRange
    }

    private fun heatmapGrid(insights: Insights): List<List<Long>> {
        val byKey = insights.heatmap.associate { (it.dayOfWeek to it.hour) to it.listenedMs }
        return DayOfWeek.entries.map { day -> (0 until HOURS).map { hour -> byKey[day to hour] ?: 0L } }
    }

    private companion object {
        const val KEY_RANGE = "range"
        const val HOURS = 24
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
