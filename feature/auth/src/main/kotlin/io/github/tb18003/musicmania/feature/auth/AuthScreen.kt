/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.component.AnimatedGradientBackground
import io.github.tb18003.musicmania.core.designsystem.component.GradientButton
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.Motion
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.ui.message
import kotlinx.coroutines.delay

@Composable
fun AuthRoute(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onAuthorizationResult(result.data)
    }
    AuthScreen(
        state = state,
        onConnect = { launcher.launch(viewModel.authorizationIntent()) },
        onDemo = viewModel::enterDemoMode,
    )
}

private data class Highlight(
    val icon: ImageVector,
    val title: Int,
    val body: Int,
)

private val highlights = listOf(
    Highlight(Icons.Rounded.AutoGraph, R.string.feature_auth_highlight_tops_title, R.string.feature_auth_highlight_tops_body),
    Highlight(Icons.Rounded.History, R.string.feature_auth_highlight_history_title, R.string.feature_auth_highlight_history_body),
    Highlight(Icons.Rounded.Lock, R.string.feature_auth_highlight_privacy_title, R.string.feature_auth_highlight_privacy_body),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuthScreen(state: AuthUiState, onConnect: () -> Unit, onDemo: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedGradientBackground(modifier.fillMaxSize(), intensity = 0.45f) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimary) }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.feature_auth_app_name), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.weight(0.6f))
            KineticHeadline(
                lines = listOf(
                    stringResource(R.string.feature_auth_headline_1),
                    stringResource(R.string.feature_auth_headline_2),
                    stringResource(R.string.feature_auth_headline_3),
                ),
            )
            Spacer(Modifier.height(28.dp))
            HighlightsPager()
            Spacer(Modifier.weight(1f))

            AnimatedVisibility(state.sessionExpired || state.error != null || !state.isConfigured, enter = fadeIn(), exit = fadeOut()) {
                val message = when {
                    state.error != null -> state.error.message()
                    state.sessionExpired -> stringResource(R.string.feature_auth_session_expired)
                    else -> stringResource(R.string.feature_auth_not_configured)
                }
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                GradientButton(
                    text = stringResource(R.string.feature_auth_connect),
                    onClick = onConnect,
                    enabled = state.isConfigured,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = onDemo, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.feature_auth_demo))
            }
            Text(
                stringResource(R.string.feature_auth_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** Each line slides up and fades in after the previous one. */
@Composable
private fun KineticHeadline(lines: List<String>) {
    val reduce = LocalReduceMotion.current
    Column {
        lines.forEachIndexed { index, line ->
            val progress = remember { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(Unit) {
                progress.animateTo(
                    1f,
                    tween(Motion.LONG, delayMillis = HEADLINE_DELAY + index * LINE_DELAY, easing = Motion.EmphasizedDecelerate),
                )
            }
            Text(
                line,
                style = MaterialTheme.typography.displayMedium,
                color = if (index == lines.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer {
                    alpha = progress.value
                    translationY = (1f - progress.value) * LINE_OFFSET_PX
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HighlightsPager() {
    val pager = rememberPagerState { highlights.size }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(pager, reduce) {
        if (reduce) return@LaunchedEffect
        while (true) {
            delay(AUTO_ADVANCE_MS)
            pager.animateScrollToPage((pager.currentPage + 1) % highlights.size)
        }
    }
    Column {
        HorizontalPager(state = pager, pageSpacing = 16.dp) { page ->
            val item = highlights[page]
            Row(verticalAlignment = Alignment.Top) {
                Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(item.body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(highlights.size) { index ->
                val selected = pager.currentPage == index
                Box(
                    Modifier
                        .height(6.dp)
                        .width(if (selected) 22.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

private const val HEADLINE_DELAY = 150
private const val LINE_DELAY = 140
private const val LINE_OFFSET_PX = 60f
private const val AUTO_ADVANCE_MS = 3_500L

@Preview
@Composable
private fun AuthScreenPreview() {
    MusicManiaTheme(darkTheme = true) { AuthScreen(AuthUiState(), onConnect = {}, onDemo = {}) }
}
