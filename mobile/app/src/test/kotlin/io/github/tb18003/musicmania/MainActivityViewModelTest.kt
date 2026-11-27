/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania

import io.github.tb18003.musicmania.core.common.config.AppInfo
import io.github.tb18003.musicmania.core.common.config.CloudConfig
import io.github.tb18003.musicmania.core.domain.usecase.CheckForAppUpdateUseCase
import io.github.tb18003.musicmania.core.model.AppUpdate
import io.github.tb18003.musicmania.core.model.AppVersion
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.repository.FakeCloudRepository
import io.github.tb18003.musicmania.core.testing.repository.FakePreferencesRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSessionRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSyncScheduler
import io.github.tb18003.musicmania.core.testing.repository.FakeUserRepository
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `signing in schedules sync and signing out cancels it`() {
        val session = FakeSessionRepository(SessionState.SignedOut)
        val scheduler = FakeSyncScheduler()
        val viewModel =
            MainActivityViewModel(session, FakePreferencesRepository(), scheduler, FakeUserRepository(), CloudConfig.Disabled, noUpdates)

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
            noUpdates,
        )
        assertFalse(scheduler.periodicScheduled)
    }

    @Test
    fun `a newer release is offered once per start and can be dismissed`() {
        val cloud = FakeCloudRepository().apply { latestVersion = "0.4.0" }
        val viewModel = viewModel(CheckForAppUpdateUseCase(cloud, AppInfo("0.3.0")))

        assertEquals(AppUpdate(AppVersion(0, 3, 0), AppVersion(0, 4, 0)), viewModel.uiState.value.update)
        viewModel.dismissUpdate()
        assertNull(viewModel.uiState.value.update)
    }

    @Test
    fun `an up-to-date app shows no dialog`() {
        val cloud = FakeCloudRepository().apply { latestVersion = "0.3.0" }
        assertNull(viewModel(CheckForAppUpdateUseCase(cloud, AppInfo("0.3.0"))).uiState.value.update)
    }

    private fun viewModel(check: CheckForAppUpdateUseCase) = MainActivityViewModel(
        FakeSessionRepository(SessionState.SignedOut),
        FakePreferencesRepository(),
        FakeSyncScheduler(),
        FakeUserRepository(),
        CloudConfig("https://api.example.test"),
        check,
    )

    /** No cloud API configured: the update check stays silent. */
    private val noUpdates = CheckForAppUpdateUseCase(FakeCloudRepository(isEnabled = false), AppInfo("0.3.0"))
}
