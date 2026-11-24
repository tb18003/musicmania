/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.common.config

/**
 * Build-time configuration of the optional MusicMania cloud API (friends leaderboard).
 * When [apiBaseUrl] is blank the app works exactly as before: fully local, no ranking tab.
 */
data class CloudConfig(val apiBaseUrl: String) {
    val isConfigured: Boolean get() = apiBaseUrl.startsWith("https://")

    /** Retrofit needs a valid base URL even when the feature is off; requests are never made then. */
    val retrofitBaseUrl: String get() = if (isConfigured) apiBaseUrl.trimEnd('/') + "/" else DISABLED_BASE_URL

    companion object {
        const val DISABLED_BASE_URL = "https://cloud.invalid/"
        val Disabled = CloudConfig("")
    }
}
