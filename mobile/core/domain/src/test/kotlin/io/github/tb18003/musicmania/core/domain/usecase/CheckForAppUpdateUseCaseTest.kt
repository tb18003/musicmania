/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.config.AppInfo
import io.github.tb18003.musicmania.core.model.AppUpdate
import io.github.tb18003.musicmania.core.model.AppVersion
import io.github.tb18003.musicmania.core.testing.repository.FakeCloudRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckForAppUpdateUseCaseTest {
    private val cloud = FakeCloudRepository()

    private suspend fun check(installed: String, latest: String?): AppUpdate? {
        cloud.latestVersion = latest
        return CheckForAppUpdateUseCase(cloud, AppInfo(installed))()
    }

    @Test
    fun `a newer release is offered`() = runTest {
        assertEquals(AppUpdate(AppVersion(0, 3, 0), AppVersion(0, 4, 0)), check("0.3.0", "0.4.0"))
        assertEquals(AppVersion(1, 0, 0), check("0.9.9", "1.0.0")?.latest)
        assertEquals(AppVersion(0, 3, 1), check("0.3.0", "0.3.1")?.latest)
    }

    @Test
    fun `versions compare numerically, not as text`() = runTest {
        assertEquals(AppVersion(0, 10, 0), check("0.9.0", "0.10.0")?.latest)
        assertNull(check("0.10.0", "0.9.0"))
    }

    @Test
    fun `nothing is offered when up to date or ahead (a development build)`() = runTest {
        assertNull(check("0.3.0", "0.3.0"))
        assertNull(check("0.4.0", "0.3.0"))
    }

    @Test
    fun `failures and malformed versions never bother the user`() = runTest {
        assertNull(check("0.3.0", null), "API unreachable")
        assertNull(check("0.3.0", "latest"), "malformed answer")
        assertNull(check("dev", "9.9.9"), "malformed installed version")
        cloud.isEnabled = false
        assertNull(check("0.3.0", "9.9.9"), "no API configured")
    }

    @Test
    fun `versions parse strictly`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals("1.2.3", AppVersion(1, 2, 3).toString())
        listOf("1.2", "1.2.3.4", "1.2.x", "", "-1.2.3").forEach { assertNull(AppVersion.parse(it), it) }
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
    }
}
