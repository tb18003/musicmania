/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.ui

import io.github.tb18003.musicmania.core.model.AppUpdate
import io.github.tb18003.musicmania.core.model.AppVersion
import io.github.tb18003.musicmania.core.model.ReleaseNotes
import org.junit.Test
import kotlin.test.assertEquals

class UpdateDialogTest {
    private fun release(minor: Int, vararg spanish: String) =
        ReleaseNotes(AppVersion(0, minor, 0), mapOf("es" to spanish.toList(), "en" to listOf("English $minor")))

    @Test
    fun `changes come newest first, in the user's language, and stay brief`() {
        val update = AppUpdate(
            current = AppVersion(0, 1, 0),
            latest = AppVersion(0, 3, 0),
            releases = listOf(release(3, "a", "b", "c", "d"), release(2, "e", "f", "g")),
        )

        assertEquals(listOf("a", "b", "c", "d", "e", "f"), changesFor(update, "es"))
        assertEquals(listOf("English 3", "English 2"), changesFor(update, "fr"))
        assertEquals(emptyList(), changesFor(update.copy(releases = emptyList()), "es"))
    }
}
