/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.domain.stats.toDateRange
import io.github.tb18003.musicmania.core.domain.stats.yearToDateRange
import io.github.tb18003.musicmania.core.domain.usecase.BuildRecapUseCase
import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.Recap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class RecapPeriod { LAST_30_DAYS, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR }

data class RecapUiState(
    val period: RecapPeriod = RecapPeriod.THIS_YEAR,
    val recap: Recap? = null,
    /** e.g. "2026" or "June 2026", used in headlines. */
    val periodLabelYear: Int = 0,
    val periodLabelMonth: YearMonth? = null,
)

@HiltViewModel
class RecapViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    buildRecap: BuildRecapUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val period = savedStateHandle.getStateFlow(KEY_PERIOD, RecapPeriod.THIS_YEAR)

    val uiState: StateFlow<RecapUiState> = period
        .flatMapLatest { p ->
            val base = labelFor(p)
            buildRecap(rangeFor(p)).map { base.copy(recap = it) }.onStart { emit(base) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecapUiState())

    fun selectPeriod(newPeriod: RecapPeriod) {
        savedStateHandle[KEY_PERIOD] = newPeriod
    }

    internal fun rangeFor(period: RecapPeriod): DateRange {
        val today = LocalDate.now(clock)
        return when (period) {
            RecapPeriod.LAST_30_DAYS -> DateRange(clock.instant().minus(Duration.ofDays(LAST_DAYS)), clock.instant().plusSeconds(1))
            RecapPeriod.THIS_MONTH -> YearMonth.from(today).toDateRange(clock)
            RecapPeriod.LAST_MONTH -> YearMonth.from(today).minusMonths(1).toDateRange(clock)
            RecapPeriod.THIS_YEAR -> yearToDateRange(today.year, clock)
            RecapPeriod.LAST_YEAR -> yearToDateRange(today.year - 1, clock)
        }
    }

    private fun labelFor(period: RecapPeriod): RecapUiState {
        val today = LocalDate.now(clock)
        return when (period) {
            RecapPeriod.THIS_MONTH -> RecapUiState(period, periodLabelMonth = YearMonth.from(today))
            RecapPeriod.LAST_MONTH -> RecapUiState(period, periodLabelMonth = YearMonth.from(today).minusMonths(1))
            RecapPeriod.THIS_YEAR -> RecapUiState(period, periodLabelYear = today.year)
            RecapPeriod.LAST_YEAR -> RecapUiState(period, periodLabelYear = today.year - 1)
            RecapPeriod.LAST_30_DAYS -> RecapUiState(period)
        }
    }

    private companion object {
        const val KEY_PERIOD = "period"
        const val STOP_TIMEOUT_MS = 5_000L
        const val LAST_DAYS = 30L
    }
}
