/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.feature.auth.AuthRoute
import kotlinx.serialization.Serializable

@Serializable
data object AuthDestination

fun NavGraphBuilder.authScreen() {
    composable<AuthDestination> { AuthRoute() }
}
