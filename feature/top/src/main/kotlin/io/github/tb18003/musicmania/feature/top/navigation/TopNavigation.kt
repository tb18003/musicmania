/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.top.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.core.designsystem.theme.ProvideEntranceOnce
import io.github.tb18003.musicmania.core.ui.ProvideNavAnimatedScope
import io.github.tb18003.musicmania.feature.top.TopActions
import io.github.tb18003.musicmania.feature.top.TopRoute
import kotlinx.serialization.Serializable

@Serializable
data object TopDestination

fun NavGraphBuilder.topScreen(actions: TopActions, snackbarHostState: SnackbarHostState) {
    composable<TopDestination> {
        ProvideEntranceOnce("top") { ProvideNavAnimatedScope { TopRoute(actions, snackbarHostState) } }
    }
}
