/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.settings

import app.cash.turbine.test
import io.github.tb18003.musicmania.core.common.config.AppInfo
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.domain.usecase.BuildSharedStatsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.PublishListeningStatsUseCase
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakeCloudRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeDataManagementRepository
import io.github.tb18003.musicmania.core.testing.repository.FakePreferencesRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSessionRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSyncScheduler
import io.github.tb18003.musicmania.core.testing.repository.FakeUserRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private val session = FakeSessionRepository()
    private val scheduler = FakeSyncScheduler().apply { periodicScheduled = true }
    private val data = FakeDataManagementRepository()

    private val cloud = FakeCloudRepository()
    private val history = InMemoryHistoryRepository()

    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy {
        SettingsViewModel(
            preferences,
            session,
            scheduler,
            data,
            cloud,
            PublishListeningStatsUseCase(cloud, session, BuildSharedStatsUseCase(history, TestData.fixedClock)),
            FakeUserRepository(),
            history,
            AppInfo("0.3.0"),
        )
    }

    /** uiState is WhileSubscribed: keep a collector alive, as the screen does. */
    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `without a cloud API there is no ranking section`() = runTest {
        cloud.isEnabled = false
        assertNull(viewModel.uiState.value.cloud)
    }

    @Test
    fun `the installed version is shown`() = runTest {
        subscribe()
        assertEquals("0.3.0", viewModel.uiState.value.appVersion)
    }

    @Test
    fun `opting in to the ranking publishes right away`() = runTest {
        subscribe()
        assertEquals(false, viewModel.uiState.value.cloud?.sharingEnabled)

        viewModel.setCloudSharing(true)

        assertEquals(true, cloud.profile.sharingEnabled)
        assertEquals(1, cloud.published.size)
        assertEquals(true, viewModel.uiState.value.cloud?.sharingEnabled)
    }

    @Test
    fun `a failed upload after opting in is reported`() = runTest {
        cloud.publishFailure = AppError.Network
        viewModel.eventFlow.test {
            viewModel.setCloudSharing(true)
            assertEquals(SettingsEvent.CloudFailed, awaitItem())
        }
        assertTrue(cloud.profile.sharingEnabled, "sharing stays on: the next sync retries the upload")
    }

    @Test
    fun `accounts outside the allowlist see why`() = runTest {
        cloud.failure = AppError.NotAllowlisted
        subscribe()
        assertEquals(true, viewModel.uiState.value.cloud?.notAllowlisted)
    }

    @Test
    fun `deleting server data reports an event`() = runTest {
        viewModel.eventFlow.test {
            viewModel.deleteCloudData()
            assertEquals(SettingsEvent.CloudDataDeleted, awaitItem())
        }
        assertTrue(cloud.deleted)
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
