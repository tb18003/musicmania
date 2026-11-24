/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.leaderboard.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.core.designsystem.theme.ProvideEntranceOnce
import io.github.tb18003.musicmania.feature.leaderboard.LeaderboardRoute
import kotlinx.serialization.Serializable

@Serializable
data object LeaderboardDestination

fun NavGraphBuilder.leaderboardScreen() {
    composable<LeaderboardDestination> { ProvideEntranceOnce("leaderboard") { LeaderboardRoute() } }
}
