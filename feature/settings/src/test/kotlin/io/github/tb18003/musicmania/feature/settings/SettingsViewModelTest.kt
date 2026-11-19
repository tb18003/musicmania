/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.settings

import app.cash.turbine.test
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.repository.FakeDataManagementRepository
import io.github.tb18003.musicmania.core.testing.repository.FakePreferencesRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSessionRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSyncScheduler
import io.github.tb18003.musicmania.core.testing.repository.FakeUserRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private val session = FakeSessionRepository()
    private val scheduler = FakeSyncScheduler().apply { periodicScheduled = true }
    private val data = FakeDataManagementRepository()

    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy {
        SettingsViewModel(preferences, session, scheduler, data, FakeUserRepository(), InMemoryHistoryRepository())
    }

    @Test
    fun `preferences are persisted`() = runTest {
        viewModel.setThemeMode(ThemeMode.LIGHT)
        viewModel.setReduceMotion(true)
        assertEquals(ThemeMode.LIGHT, preferences.state.value.themeMode)
        assertTrue(preferences.state.value.reduceMotion)
    }

    @Test
    fun `sign out cancels sync and ends the session`() = runTest {
        viewModel.signOut()
        assertFalse(scheduler.periodicScheduled)
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    @Test
    fun `export and delete report events`() = runTest {
        viewModel.eventFlow.test {
            val output = ByteArrayOutputStream()
            viewModel.export { output }
            assertEquals(SettingsEvent.Exported(0), awaitItem())
            assertEquals("[]", output.toString())

            viewModel.export { null }
            assertEquals(SettingsEvent.ExportFailed, awaitItem())

            viewModel.deleteLocalData()
            assertEquals(SettingsEvent.DataDeleted, awaitItem())
        }
        assertTrue(data.deleted)
    }
}
