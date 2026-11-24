/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.auth.SpotifyAuthManager
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.SessionRepository
import io.github.tb18003.musicmania.core.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isConfigured: Boolean = true,
    val isLoading: Boolean = false,
    val sessionExpired: Boolean = false,
    val error: AppError? = null,
)

/** Contract the screen needs from the OAuth layer (keeps the ViewModel testable). */
interface AuthGateway {
    val isConfigured: Boolean

    fun createAuthorizationIntent(): Intent

    suspend fun handleAuthorizationResult(data: Intent?): DataResult<Unit>
}

internal class SpotifyAuthGateway @Inject constructor(private val manager: SpotifyAuthManager) : AuthGateway {
    override val isConfigured: Boolean get() = manager.isConfigured

    override fun createAuthorizationIntent(): Intent = manager.createAuthorizationIntent()

    override suspend fun handleAuthorizationResult(data: Intent?) = manager.handleAuthorizationResult(data)
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val gateway: AuthGateway,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val local = MutableStateFlow(AuthUiState(isConfigured = gateway.isConfigured))

    val uiState: StateFlow<AuthUiState> = combine(local, sessionRepository.session) { state, session ->
        state.copy(sessionExpired = session == SessionState.Expired)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    fun authorizationIntent(): Intent {
        local.update { it.copy(isLoading = true, error = null) }
        return gateway.createAuthorizationIntent()
    }

    fun onAuthorizationResult(data: Intent?) {
        viewModelScope.launch {
            val result = gateway.handleAuthorizationResult(data)
            local.update {
                it.copy(
                    isLoading = false,
                    // A user closing the browser is not an error worth shouting about.
                    error = (result as? DataResult.Failure)?.error?.takeUnless { e ->
                        e is AppError.Unknown && e.description == "cancelled"
                    },
                )
            }
        }
    }

    fun enterDemoMode() {
        viewModelScope.launch {
            local.update { it.copy(isLoading = true) }
            sessionRepository.enterDemoMode()
            local.update { it.copy(isLoading = false) }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
