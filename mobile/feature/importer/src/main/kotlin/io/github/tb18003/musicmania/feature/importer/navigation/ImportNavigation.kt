/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.importer.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.feature.importer.ImportRoute
import kotlinx.serialization.Serializable

@Serializable
data object ImportDestination

fun NavGraphBuilder.importScreen(onBack: () -> Unit) {
    composable<ImportDestination> { ImportRoute(onBack) }
}
