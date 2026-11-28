/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api

import io.github.tb18003.musicmania.api.model.ReleaseNotes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BuildInfoTest {
    @Test
    fun `the build carries the repository version from web package json`() {
        val packageJson = File("../web/package.json").readText()
        val expected = Regex(""""version"\s*:\s*"([^"]+)"""").find(packageJson)?.groupValues?.get(1)
        assertEquals(expected, BuildInfo.version)
    }

    @Test
    fun `every release has short notes in English and Spanish, starting with the current one`() {
        val notes = BuildInfo.releaseNotes
        assertEquals(BuildInfo.version, notes.first().version, "add the notes of ${BuildInfo.version} to api/release-notes.json")
        notes.forEach { release ->
            assertNotNull(BuildInfo.semver(release.version), "${release.version} is not MAJOR.MINOR.PATCH")
            listOf("en", "es").forEach { language ->
                val lines = release.notes[language].orEmpty()
                assertTrue(lines.isNotEmpty(), "${release.version} has no '$language' notes")
                assertTrue(
                    lines.all {
                        it.isNotBlank() && it.length <= MAX_LINE
                    },
                    "${release.version}/$language: lines of 1-$MAX_LINE characters",
                )
            }
        }
    }

    @Test
    fun `only releases up to the current one are listed, newest first`() {
        val notes = listOf("0.3.0", "0.10.0", "0.9.1", "1.0.0", "bad").map { ReleaseNotes(it, emptyMap()) }
        assertEquals(listOf("0.10.0", "0.9.1", "0.3.0"), BuildInfo.upTo("0.10.0", notes).map { it.version })
        assertEquals(emptyList<ReleaseNotes>(), BuildInfo.upTo("not-a-version", notes))
    }

    private companion object {
        const val MAX_LINE = 140
    }
}
