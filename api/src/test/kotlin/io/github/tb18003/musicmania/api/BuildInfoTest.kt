/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class BuildInfoTest {
    @Test
    fun `the build carries the repository version from web package json`() {
        val packageJson = File("../web/package.json").readText()
        val expected = Regex(""""version"\s*:\s*"([^"]+)"""").find(packageJson)?.groupValues?.get(1)
        assertEquals(expected, BuildInfo.version)
    }
}
