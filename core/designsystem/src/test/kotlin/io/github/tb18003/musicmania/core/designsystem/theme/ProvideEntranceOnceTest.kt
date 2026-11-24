/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.tb18003.musicmania.core.designsystem.component.AnimatedCounter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProvideEntranceOnceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `counter animates on the first visit and shows its value directly on a revisit`() {
        val registry = EntranceRegistry()
        var visible by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalEntranceRegistry provides registry) {
                if (visible) {
                    ProvideEntranceOnce("stats") { AnimatedCounter(value = 1_234) }
                }
            }
        }

        // First visit: the counter starts from zero and counts up.
        compose.onNodeWithText("0").assertExists()
        compose.mainClock.advanceTimeBy(COUNTER_DURATION_MS)
        compose.onNodeWithText("1,234").assertExists()

        // Leave and come back (like switching tabs): the value is shown right away.
        visible = false
        compose.mainClock.advanceTimeByFrame()
        visible = true
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("1,234").assertExists()
    }

    private companion object {
        const val COUNTER_DURATION_MS = Motion.EXTRA_LONG + 100L
    }
}
