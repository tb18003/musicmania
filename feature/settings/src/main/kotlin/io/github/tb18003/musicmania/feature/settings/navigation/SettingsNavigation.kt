/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.settings.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onImport: () -> Unit, snackbarHostState: SnackbarHostState) {
    composable<SettingsDestination> { SettingsRoute(onBack, onImport, snackbarHostState) }
}
