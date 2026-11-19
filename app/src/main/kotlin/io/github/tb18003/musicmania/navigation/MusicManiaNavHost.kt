/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.Motion
import io.github.tb18003.musicmania.core.ui.LocalSharedTransitionScope
import io.github.tb18003.musicmania.feature.detail.navigation.detailScreen
import io.github.tb18003.musicmania.feature.detail.navigation.navigateToAlbum
import io.github.tb18003.musicmania.feature.detail.navigation.navigateToArtist
import io.github.tb18003.musicmania.feature.detail.navigation.navigateToTrack
import io.github.tb18003.musicmania.feature.history.navigation.historyScreen
import io.github.tb18003.musicmania.feature.home.HomeActions
import io.github.tb18003.musicmania.feature.home.navigation.HomeDestination
import io.github.tb18003.musicmania.feature.home.navigation.homeScreen
import io.github.tb18003.musicmania.feature.importer.navigation.ImportDestination
import io.github.tb18003.musicmania.feature.importer.navigation.importScreen
import io.github.tb18003.musicmania.feature.insights.navigation.insightsScreen
import io.github.tb18003.musicmania.feature.recap.navigation.RecapDestination
import io.github.tb18003.musicmania.feature.recap.navigation.recapScreen
import io.github.tb18003.musicmania.feature.settings.navigation.SettingsDestination
import io.github.tb18003.musicmania.feature.settings.navigation.settingsScreen
import io.github.tb18003.musicmania.feature.top.TopActions
import io.github.tb18003.musicmania.feature.top.navigation.topScreen

/**
 * The only place that knows every feature: it wires their navigation callbacks
 * together (features never import each other). Wrapped in a SharedTransitionLayout
 * so artwork can morph between list and detail screens.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MusicManiaNavHost(navController: NavHostController, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val homeActions = HomeActions(
        onTrackClick = { navController.navigateToTrack(it.id, it.name, it.primaryArtistName, it.album.imageUrl) },
        onArtistClick = { navController.navigateToArtist(it.id, it.name, it.imageUrl) },
        onSeeAllTop = { navController.navigateToTopLevel(TopLevelDestination.TOP) },
        onOpenRecap = { navController.navigate(RecapDestination) },
        onOpenInsights = { navController.navigateToTopLevel(TopLevelDestination.INSIGHTS) },
        onOpenImport = { navController.navigate(ImportDestination) },
        onOpenSettings = { navController.navigate(SettingsDestination) },
    )
    val topActions = TopActions(
        onTrackClick = { navController.navigateToTrack(it.id, it.name, it.primaryArtistName, it.album.imageUrl) },
        onArtistClick = { navController.navigateToArtist(it.id, it.name, it.imageUrl) },
        onAlbumClick = { navController.navigateToAlbum(it.album.id, it.album.name, it.artistName, it.album.imageUrl) },
    )
    val openImport: () -> Unit = { navController.navigate(ImportDestination) }

    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = HomeDestination,
                enterTransition = {
                    if (reduceMotion) {
                        EnterTransition.None
                    } else {
                        fadeIn(tween(Motion.MEDIUM)) +
                            scaleIn(tween(Motion.MEDIUM), initialScale = 0.96f)
                    }
                },
                exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.SHORT)) },
                popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.MEDIUM)) },
                popExitTransition = {
                    if (reduceMotion) {
                        ExitTransition.None
                    } else {
                        fadeOut(tween(Motion.SHORT)) +
                            scaleOut(tween(Motion.MEDIUM), targetScale = 0.96f)
                    }
                },
            ) {
                homeScreen(homeActions, snackbarHostState)
                topScreen(topActions, snackbarHostState)
                historyScreen(
                    onPlayClick = { navController.navigateToTrack(it.trackId, it.trackName, it.artistName, it.imageUrl) },
                    onImport = openImport,
                )
                insightsScreen(onImport = openImport)
                detailScreen(onBack = navController::popBackStack)
                recapScreen(onClose = navController::popBackStack, onImport = openImport)
                importScreen(onBack = navController::popBackStack)
                settingsScreen(onBack = navController::popBackStack, onImport = openImport, snackbarHostState = snackbarHostState)
            }
        }
    }
}

/** Bottom-bar navigation: single top, state saved/restored per tab. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
