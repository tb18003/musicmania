/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.history.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.core.designsystem.theme.ProvideEntranceOnce
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.ui.ProvideNavAnimatedScope
import io.github.tb18003.musicmania.feature.history.HistoryRoute
import kotlinx.serialization.Serializable

@Serializable
data object HistoryDestination

fun NavGraphBuilder.historyScreen(onPlayClick: (PlayEvent) -> Unit, onImport: () -> Unit) {
    composable<HistoryDestination> {
        ProvideEntranceOnce("history") { ProvideNavAnimatedScope { HistoryRoute(onPlayClick, onImport) } }
    }
}
