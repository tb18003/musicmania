/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.repository.SyncScheduler
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Items of the history timeline: day headers interleaved with plays. */
sealed interface HistoryItem {
    data class DayHeader(val date: LocalDate) : HistoryItem

    data class Play(val event: PlayEvent) : HistoryItem
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) : ViewModel() {
    val items: Flow<PagingData<HistoryItem>> = historyRepository.pagedHistory()
        .map { paging -> paging.map<PlayEvent, HistoryItem> { HistoryItem.Play(it) }.withDayHeaders() }
        .cachedIn(viewModelScope)

    val syncStatus: StateFlow<SyncStatus> = syncScheduler.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncStatus(null, false))

    val eventCount: StateFlow<Int?> = historyRepository.observeEventCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun syncNow() = syncScheduler.requestSyncNow()

    private fun PagingData<HistoryItem>.withDayHeaders(): PagingData<HistoryItem> = insertSeparators { before, after ->
        val afterDate = (after as? HistoryItem.Play)?.event?.playedAt?.atZone(clock.zone)?.toLocalDate() ?: return@insertSeparators null
        val beforeDate = (before as? HistoryItem.Play)?.event?.playedAt?.atZone(clock.zone)?.toLocalDate()
        if (beforeDate != afterDate) HistoryItem.DayHeader(afterDate) else null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
