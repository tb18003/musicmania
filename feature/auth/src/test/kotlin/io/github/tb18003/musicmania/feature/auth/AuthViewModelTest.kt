/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.auth

import android.content.Intent
import app.cash.turbine.test
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.repository.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeGateway(var result: DataResult<Unit> = DataResult.Success(Unit), override val isConfigured: Boolean = true) :
        AuthGateway {
        override fun createAuthorizationIntent() = Intent("auth")

        override suspend fun handleAuthorizationResult(data: Intent?) = result
    }

    @Test
    fun `failed login surfaces the error and stops loading`() = runTest {
        val gateway = FakeGateway(DataResult.Failure(AppError.Network))
        val viewModel = AuthViewModel(gateway, FakeSessionRepository(SessionState.SignedOut))

        viewModel.uiState.test {
            assertEquals(AuthUiState(), awaitItem())
            viewModel.authorizationIntent()
            assertTrue(awaitItem().isLoading)
            viewModel.onAuthorizationResult(null)
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(AppError.Network, state.error)
        }
    }

    @Test
    fun `cancelling the browser is not reported as an error`() = runTest {
        val gateway = FakeGateway(DataResult.Failure(AppError.Unknown("cancelled")))
        val viewModel = AuthViewModel(gateway, FakeSessionRepository(SessionState.SignedOut))

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAuthorizationResult(null)
            // State is unchanged, so nothing is emitted and no error is shown.
            expectNoEvents()
            assertNull(viewModel.uiState.value.error)
        }
    }

    @Test
    fun `expired sessions are flagged and demo mode switches session`() = runTest {
        val session = FakeSessionRepository(SessionState.Expired)
        val viewModel = AuthViewModel(FakeGateway(), session)

        viewModel.uiState.test {
            assertTrue(awaitItem().sessionExpired)
            viewModel.enterDemoMode()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SessionState.Demo, session.state.value)
    }

    @Test
    fun `missing credentials disable login`() {
        val viewModel = AuthViewModel(FakeGateway(isConfigured = false), FakeSessionRepository(SessionState.SignedOut))
        assertFalse(viewModel.uiState.value.isConfigured)
    }
}
