/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.tb18003.musicmania.core.designsystem.theme.Motion
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.feature.auth.AuthRoute
import io.github.tb18003.musicmania.navigation.MusicManiaNavHost
import io.github.tb18003.musicmania.navigation.TopLevelDestination
import io.github.tb18003.musicmania.navigation.navigateToTopLevel

/** Root composable: switches between onboarding and the main app with a cross-fade. */
@Composable
fun MusicManiaApp(session: SessionState) {
    AnimatedContent(
        targetState = when {
            session == SessionState.Loading -> AppStage.LOADING
            session.hasData -> AppStage.MAIN
            else -> AppStage.AUTH
        },
        transitionSpec = { fadeIn(tween(Motion.LONG)) togetherWith fadeOut(tween(Motion.MEDIUM)) },
        label = "appStage",
    ) { stage ->
        when (stage) {
            AppStage.LOADING -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            AppStage.AUTH -> AuthRoute()
            AppStage.MAIN -> MainScaffold()
        }
    }
}

private enum class AppStage { LOADING, AUTH, MAIN }

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.isOn(destination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, null) },
                            label = { Text(stringResource(destination.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        MusicManiaNavHost(navController, snackbarHostState, Modifier.padding(bottom = padding.calculateBottomPadding()))
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
