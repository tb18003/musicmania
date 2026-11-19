/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.ui

import io.github.tb18003.musicmania.core.model.RankMovement
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormattersTest {
    @Test
    fun `compact numbers`() {
        assertEquals("999", Formatters.compact(999, Locale.US))
        assertEquals("1.2K", Formatters.compact(1_234, Locale.US))
        assertEquals("2.5M", Formatters.compact(2_500_000, Locale.US))
        assertEquals("1,2K", Formatters.compact(1_234, Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun `track duration`() {
        assertEquals("3:07", Formatters.trackDuration(187_000))
        assertEquals("0:05", Formatters.trackDuration(5_400))
    }

    @Test
    fun `minutes and numbers`() {
        assertEquals(12L, Formatters.minutes(720_000))
        assertEquals("12,840", Formatters.number(12_840, Locale.US))
    }

    @Test
    fun `dates use the given zone`() {
        val instant = Instant.parse("2024-12-31T23:30:00Z")
        assertEquals("Dec 31, 2024", Formatters.date(instant, ZoneOffset.UTC, Locale.US))
    }

    @Test
    fun `movement labels`() {
        assertEquals("+3", Formatters.movement(RankMovement.Up(3)))
        assertEquals("−2", Formatters.movement(RankMovement.Down(2)))
        assertNull(Formatters.movement(RankMovement.Same))
    }
}
