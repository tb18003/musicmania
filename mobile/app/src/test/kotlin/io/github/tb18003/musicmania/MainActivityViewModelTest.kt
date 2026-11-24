/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania

import io.github.tb18003.musicmania.core.common.config.CloudConfig
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.repository.FakePreferencesRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSessionRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSyncScheduler
import io.github.tb18003.musicmania.core.testing.repository.FakeUserRepository
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `signing in schedules sync and signing out cancels it`() {
        val session = FakeSessionRepository(SessionState.SignedOut)
        val scheduler = FakeSyncScheduler()
        val viewModel = MainActivityViewModel(session, FakePreferencesRepository(), scheduler, FakeUserRepository(), CloudConfig.Disabled)

        session.state.value = SessionState.SignedIn
        assertTrue(scheduler.periodicScheduled)
        assertEquals(SessionState.SignedIn, viewModel.uiState.value.session)

        session.state.value = SessionState.SignedOut
        assertFalse(scheduler.periodicScheduled)
    }

    @Test
    fun `demo mode never schedules network sync`() {
        val scheduler = FakeSyncScheduler()
        MainActivityViewModel(
            FakeSessionRepository(SessionState.Demo),
            FakePreferencesRepository(),
            scheduler,
            FakeUserRepository(),
            CloudConfig.Disabled,
        )
        assertFalse(scheduler.periodicScheduled)
    }
}
