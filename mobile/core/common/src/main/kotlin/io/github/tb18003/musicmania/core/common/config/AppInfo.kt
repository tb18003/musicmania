/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.common.config

/** Facts about the installed build, provided by the app module (features cannot read its BuildConfig). */
data class AppInfo(
    /** The release version, MAJOR.MINOR.PATCH: the repository's version in web/package.json. */
    val versionName: String,
) {
    companion object {
        /** Where new versions are published (the update dialog and Settings link here). */
        const val RELEASES_URL = "https://github.com/tb18003/musicmania/releases"
    }
}
