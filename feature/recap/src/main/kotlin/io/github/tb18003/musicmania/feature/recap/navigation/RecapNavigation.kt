/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.recap.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.feature.recap.RecapRoute
import kotlinx.serialization.Serializable

@Serializable
data object RecapDestination

fun NavGraphBuilder.recapScreen(onClose: () -> Unit, onImport: () -> Unit) {
    composable<RecapDestination>(
        enterTransition = { scaleIn(initialScale = 0.9f) + fadeIn() },
        exitTransition = { fadeOut() },
        popExitTransition = { scaleOut(targetScale = 0.9f) + fadeOut() },
    ) { RecapRoute(onClose, onImport) }
}
