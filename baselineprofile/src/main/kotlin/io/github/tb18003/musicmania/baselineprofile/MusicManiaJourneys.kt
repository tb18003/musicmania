/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Application id of the app under test (`targetProjectPath = ":app"`). */
internal const val PACKAGE_NAME = "io.github.tb18003.musicmania"

private const val UI_TIMEOUT_MS = 10_000L
private const val SEED_TIMEOUT_MS = 30_000L

/** Mirrors the test tags in `AuthScreen` and `MusicManiaApp` (exposed via testTagsAsResourceId). */
private const val DEMO_BUTTON = "auth_demo"
private val TOP_LEVEL_TABS = listOf("nav_top", "nav_history", "nav_insights", "nav_home")

/**
 * Signs in to demo mode on a fresh install. Demo mode seeds the real database, so the
 * profile covers the same code paths as a signed-in user, without Spotify credentials.
 */
internal fun MacrobenchmarkScope.enterDemoModeIfNeeded() {
    device.wait(Until.hasObject(By.res(DEMO_BUTTON).enabled(true)), UI_TIMEOUT_MS)
    device.findObject(By.res(DEMO_BUTTON))?.click() ?: return
    device.wait(Until.hasObject(By.res(TOP_LEVEL_TABS.first())), SEED_TIMEOUT_MS)
}

/** Visits every bottom-bar destination and scrolls its content, ending back on Home. */
internal fun MacrobenchmarkScope.browseTopLevelDestinations() {
    scrollCurrentScreen()
    TOP_LEVEL_TABS.forEach { tag ->
        device.wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MS)
        device.findObject(By.res(tag))?.click()
        device.waitForIdle()
        scrollCurrentScreen()
    }
}

private fun MacrobenchmarkScope.scrollCurrentScreen() {
    if (!device.wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)) return
    // Coordinate swipes (no node references): screens recompose while data loads, which makes
    // UiObject2 handles stale. The 30-70 % band stays clear of the system bars.
    val x = device.displayWidth / 2
    val upper = device.displayHeight * SWIPE_UPPER_PERCENT / PERCENT
    val lower = device.displayHeight * SWIPE_LOWER_PERCENT / PERCENT
    device.swipe(x, lower, x, upper, SWIPE_STEPS)
    device.waitForIdle()
    device.swipe(x, upper, x, lower, SWIPE_STEPS)
    device.waitForIdle()
}

private const val PERCENT = 100
private const val SWIPE_UPPER_PERCENT = 30
private const val SWIPE_LOWER_PERCENT = 70
private const val SWIPE_STEPS = 12
