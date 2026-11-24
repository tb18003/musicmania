/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.tb18003.musicmania.R
import io.github.tb18003.musicmania.feature.history.navigation.HistoryDestination
import io.github.tb18003.musicmania.feature.home.navigation.HomeDestination
import io.github.tb18003.musicmania.feature.insights.navigation.InsightsDestination
import io.github.tb18003.musicmania.feature.top.navigation.TopDestination
import kotlin.reflect.KClass

/** Destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val label: Int,
) {
    HOME(HomeDestination, HomeDestination::class, Icons.Rounded.Home, Icons.Outlined.Home, R.string.nav_home),
    TOP(TopDestination, TopDestination::class, Icons.Rounded.Leaderboard, Icons.Outlined.Leaderboard, R.string.nav_top),
    HISTORY(HistoryDestination, HistoryDestination::class, Icons.Rounded.History, Icons.Outlined.History, R.string.nav_history),
    INSIGHTS(InsightsDestination, InsightsDestination::class, Icons.Rounded.Insights, Icons.Outlined.Insights, R.string.nav_insights),
}
