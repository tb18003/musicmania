/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.common.config.CloudConfig
import io.github.tb18003.musicmania.core.domain.repository.PreferencesRepository
import io.github.tb18003.musicmania.core.domain.repository.SessionRepository
import io.github.tb18003.musicmania.core.domain.repository.SyncScheduler
import io.github.tb18003.musicmania.core.domain.repository.UserRepository
import io.github.tb18003.musicmania.core.domain.usecase.CheckForAppUpdateUseCase
import io.github.tb18003.musicmania.core.model.AppUpdate
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val session: SessionState = SessionState.Loading,
    val preferences: UserPreferences = UserPreferences(),
    /** A newer release to offer in a dialog; null when up to date or once dismissed. */
    val update: AppUpdate? = null,
)

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    preferencesRepository: PreferencesRepository,
    private val syncScheduler: SyncScheduler,
    private val userRepository: UserRepository,
    cloudConfig: CloudConfig,
    private val checkForAppUpdate: CheckForAppUpdateUseCase,
) : ViewModel() {
    /** The friends leaderboard tab only exists when the cloud API is configured. */
    val cloudEnabled: Boolean = cloudConfig.isConfigured

    private val update = MutableStateFlow<AppUpdate?>(null)

    val uiState: StateFlow<MainUiState> = combine(sessionRepository.session, preferencesRepository.preferences, update, ::MainUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    init {
        // Once per app start: the API announces the latest release (only when the cloud API is configured).
        viewModelScope.launch { update.value = checkForAppUpdate() }
        // Side effects of session changes live here, in one place.
        viewModelScope.launch {
            sessionRepository.session.distinctUntilChanged().collect { session ->
                when (session) {
                    SessionState.SignedIn -> {
                        syncScheduler.schedulePeriodicSync()
                        userRepository.refresh()
                    }
                    SessionState.Demo -> userRepository.refresh()
                    SessionState.SignedOut, SessionState.Expired -> syncScheduler.cancelAll()
                    SessionState.Loading -> Unit
                }
            }
        }
    }

    /** "Later" or "Download": the dialog does not come back until the next app start. */
    fun dismissUpdate() {
        update.value = null
    }
}
