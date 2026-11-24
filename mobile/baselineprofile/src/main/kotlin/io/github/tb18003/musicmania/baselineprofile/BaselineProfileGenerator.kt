/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile (and startup profile for DEX layout) from the critical
 * user journeys: cold start, onboarding, demo sign-in and every top-level screen.
 *
 * Regenerate after significant UI changes and commit the result:
 * `./gradlew :app:generateReleaseBaselineProfile` (managed device), or append
 * `-Pmusicmania.baselineprofile.connected` to use a running emulator/phone (API 33+).
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        enterDemoModeIfNeeded()
        browseTopLevelDestinations()
    }
}
