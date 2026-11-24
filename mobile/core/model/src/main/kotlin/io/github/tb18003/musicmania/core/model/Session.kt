/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

import java.time.Instant

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    /** The refresh token was rejected; the user must sign in again. */
    data object Expired : SessionState

    data object SignedIn : SessionState

    /** Explore the app with locally generated sample data, no Spotify account needed. */
    data object Demo : SessionState

    val hasData: Boolean get() = this == SignedIn || this == Demo
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val useDynamicColor: Boolean = false,
    val reduceMotion: Boolean = false,
)

data class SyncStatus(
    val lastSuccessfulSync: Instant?,
    val isRunning: Boolean,
)
