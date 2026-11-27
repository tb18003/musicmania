/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api

import java.util.Properties

/**
 * Facts about this build. The version is the repository's single release version (`web/package.json`),
 * written into the jar by the `generateBuildInfo` Gradle task.
 */
object BuildInfo {
    const val RESOURCE = "musicmania-build.properties"

    val version: String by lazy {
        val properties = Properties()
        BuildInfo::class.java.classLoader.getResourceAsStream(RESOURCE)?.use(properties::load)
        requireNotNull(properties.getProperty("version")) { "$RESOURCE is missing from the build" }
    }
}
