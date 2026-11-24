/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.theme

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntranceStateTest {
    private var now = 0L
    private val clock = { now }

    private fun state(firstVisit: Boolean) = EntranceState(firstVisit = firstVisit, startedAtNanos = 0L, clock = clock)

    private fun afterInitialPhase() {
        now = EntranceState.INITIAL_PHASE_NANOS + 1
    }

    @Test
    fun `unscoped content always animates`() {
        assertTrue(EntranceState.Unscoped.shouldPlayEntrance())
        assertTrue(EntranceState.Unscoped.shouldAnimateDataChange())
    }

    @Test
    fun `first visit animates entrances only during the initial phase`() {
        val state = state(firstVisit = true)
        assertTrue(state.shouldPlayEntrance())
        assertTrue(state.shouldAnimateDataChange())

        afterInitialPhase()
        assertFalse(state.shouldPlayEntrance(), "items scrolled into view later just appear")
        assertTrue(state.shouldAnimateDataChange())
    }

    @Test
    fun `revisit shows the reload in its final state but animates later data changes`() {
        val state = state(firstVisit = false)
        assertFalse(state.shouldPlayEntrance())
        assertFalse(state.shouldAnimateDataChange())

        afterInitialPhase()
        assertFalse(state.shouldPlayEntrance())
        assertTrue(state.shouldAnimateDataChange())
    }

    @Test
    fun `registry remembers seen screens and survives saving`() {
        val registry = EntranceRegistry()
        assertFalse(registry.hasSeen("top"))
        registry.markSeen("top")
        assertTrue(registry.hasSeen("top"))

        val saved = with(EntranceRegistry.Saver) { SaverScope { true }.save(registry) }
        val restored = EntranceRegistry.Saver.restore(requireNotNull(saved))
        assertEquals(true, restored?.hasSeen("top"))
        assertEquals(false, restored?.hasSeen("home"))
    }
}
