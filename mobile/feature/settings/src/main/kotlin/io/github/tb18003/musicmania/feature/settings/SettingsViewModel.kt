/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.CloudRepository
import io.github.tb18003.musicmania.core.domain.repository.DataManagementRepository
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.repository.PreferencesRepository
import io.github.tb18003.musicmania.core.domain.repository.SessionRepository
import io.github.tb18003.musicmania.core.domain.repository.SyncScheduler
import io.github.tb18003.musicmania.core.domain.repository.UserRepository
import io.github.tb18003.musicmania.core.domain.usecase.PublishListeningStatsUseCase
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.SyncStatus
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.model.UserPreferences
import io.github.tb18003.musicmania.core.model.UserProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val profile: UserProfile? = null,
    val session: SessionState = SessionState.Loading,
    val sync: SyncStatus = SyncStatus(null, false),
    val playCount: Int = 0,
    /** Null when the optional cloud API is not configured (no ranking section). */
    val cloud: CloudSettings? = null,
)

/** Friends-leaderboard settings. [sharingEnabled] is null while unknown or when not allow-listed. */
data class CloudSettings(
    val sharingEnabled: Boolean? = null,
    val notAllowlisted: Boolean = false,
    val busy: Boolean = false,
)

sealed interface SettingsEvent {
    data class Exported(val count: Int) : SettingsEvent

    data object ExportFailed : SettingsEvent

    data object DataDeleted : SettingsEvent

    data object CloudDataDeleted : SettingsEvent

    data object CloudFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val session: SessionRepository,
    private val syncScheduler: SyncScheduler,
    private val dataManagement: DataManagementRepository,
    private val cloud: CloudRepository,
    private val publishStats: PublishListeningStatsUseCase,
    userRepository: UserRepository,
    historyRepository: HistoryRepository,
) : ViewModel() {
    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SettingsEvent> = events.receiveAsFlow()

    private val cloudSettings = MutableStateFlow(if (cloud.isEnabled) CloudSettings() else null)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferences.preferences,
            userRepository.profile,
            session.session,
            syncScheduler.status,
            historyRepository.observeEventCount(),
        ) { prefs, profile, sessionState, sync, count -> SettingsUiState(prefs, profile, sessionState, sync, count) },
        cloudSettings,
    ) { state, cloudState -> state.copy(cloud = cloudState) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState(cloud = cloudSettings.value))

    init {
        if (cloud.isEnabled) refreshCloud()
    }

    /** Opt in or out of the friends leaderboard; opting in publishes right away. */
    fun setCloudSharing(enabled: Boolean) = viewModelScope.launch {
        cloudSettings.update { it?.copy(busy = true) }
        when (val result = cloud.setSharing(enabled)) {
            is DataResult.Success -> {
                if (enabled) publishStats()
                cloudSettings.value = CloudSettings(sharingEnabled = result.data.sharingEnabled)
            }
            is DataResult.Failure -> {
                cloudSettings.value = settingsFor(result.error)
                events.send(SettingsEvent.CloudFailed)
            }
        }
    }

    fun deleteCloudData() = viewModelScope.launch {
        cloudSettings.update { it?.copy(busy = true) }
        val result = cloud.deleteMyData()
        events.send(if (result is DataResult.Success) SettingsEvent.CloudDataDeleted else SettingsEvent.CloudFailed)
        refreshCloud()
    }

    private fun refreshCloud() {
        viewModelScope.launch {
            cloudSettings.value = when (val me = cloud.me()) {
                is DataResult.Success -> CloudSettings(sharingEnabled = me.data.sharingEnabled)
                is DataResult.Failure -> settingsFor(me.error)
            }
        }
    }

    private fun settingsFor(error: AppError) = CloudSettings(notAllowlisted = error == AppError.NotAllowlisted)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { preferences.setDynamicColor(enabled) }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch { preferences.setReduceMotion(enabled) }

    fun syncNow() = syncScheduler.requestSyncNow()

    /** [openStream] is provided by the UI from a SAF `CreateDocument` result. */
    fun export(openStream: () -> OutputStream?) = viewModelScope.launch {
        val stream = openStream()
        val result = stream?.use { dataManagement.exportHistory(it) }
        events.send(if (result is DataResult.Success) SettingsEvent.Exported(result.data) else SettingsEvent.ExportFailed)
    }

    fun deleteLocalData() = viewModelScope.launch {
        dataManagement.deleteAllLocalData()
        events.send(SettingsEvent.DataDeleted)
    }

    fun signOut() = viewModelScope.launch {
        syncScheduler.cancelAll()
        session.signOut()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
