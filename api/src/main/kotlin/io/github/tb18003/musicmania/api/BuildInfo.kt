/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api

import io.github.tb18003.musicmania.api.model.ReleaseNotes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Properties

/**
 * Facts about this build, written into the jar by the `generateBuildInfo` Gradle task: the repository's
 * single release version (`web/package.json`) and the release notes (`api/release-notes.json`).
 */
object BuildInfo {
    const val RESOURCE = "musicmania-build.properties"
    const val RELEASE_NOTES = "musicmania-release-notes.json"

    /** How many past releases the API lists (an app further behind still sees the latest ones). */
    private const val MAX_RELEASES = 10

    val version: String by lazy {
        val properties = Properties()
        resource(RESOURCE)?.use(properties::load)
        requireNotNull(properties.getProperty("version")) { "$RESOURCE is missing from the build" }
    }

    /** The notes of this release and the ones before it, newest first. */
    val releaseNotes: List<ReleaseNotes> by lazy {
        val text = requireNotNull(resource(RELEASE_NOTES)) { "$RELEASE_NOTES is missing from the build" }
            .use { it.readBytes().decodeToString() }
        val all = Json.decodeFromString(ListSerializer(ReleaseNotes.serializer()), text)
        upTo(version, all)
    }

    /** Releases at or below [version], newest first, at most [MAX_RELEASES]. */
    fun upTo(version: String, notes: List<ReleaseNotes>): List<ReleaseNotes> {
        val current = semver(version) ?: return emptyList()
        return notes
            .mapNotNull { release -> semver(release.version)?.let { it to release } }
            .filter { (releaseVersion, _) -> compare(releaseVersion, current) <= 0 }
            .sortedWith { a, b -> compare(b.first, a.first) }
            .map { it.second }
            .take(MAX_RELEASES)
    }

    fun semver(value: String): List<Int>? = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(value)?.groupValues?.drop(1)?.map(String::toInt)

    private fun compare(a: List<Int>, b: List<Int>): Int = a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0

    private fun resource(name: String) = BuildInfo::class.java.classLoader.getResourceAsStream(name)
}
