/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.home.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.core.designsystem.theme.ProvideEntranceOnce
import io.github.tb18003.musicmania.core.ui.ProvideNavAnimatedScope
import io.github.tb18003.musicmania.feature.home.HomeActions
import io.github.tb18003.musicmania.feature.home.HomeRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

fun NavGraphBuilder.homeScreen(actions: HomeActions, snackbarHostState: SnackbarHostState) {
    composable<HomeDestination> {
        ProvideEntranceOnce("home") { ProvideNavAnimatedScope { HomeRoute(actions, snackbarHostState) } }
    }
}
