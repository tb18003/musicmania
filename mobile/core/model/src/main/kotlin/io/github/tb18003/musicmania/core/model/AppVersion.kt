/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

/** A MAJOR.MINOR.PATCH release version (the repository's version, shared by the app and the web). */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""(\d{1,6})\.(\d{1,6})\.(\d{1,6})""")

        /** Parses "1.2.3" (a leading "v" is accepted); null for anything else. */
        fun parse(value: String): AppVersion? = PATTERN.matchEntire(value.trim().removePrefix("v"))?.destructured
            ?.let { (major, minor, patch) -> AppVersion(major.toInt(), minor.toInt(), patch.toInt()) }
    }
}

/** A newer release than the installed one. */
data class AppUpdate(
    val current: AppVersion,
    val latest: AppVersion,
)
