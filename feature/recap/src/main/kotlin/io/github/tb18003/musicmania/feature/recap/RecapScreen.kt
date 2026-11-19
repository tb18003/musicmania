/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.component.AnimatedGradientBackground
import io.github.tb18003.musicmania.core.designsystem.component.gradientFor
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.Recap
import io.github.tb18003.musicmania.core.ui.EmptyState
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun RecapRoute(onClose: () -> Unit, onImport: () -> Unit, viewModel: RecapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecapScreen(state, onSelectPeriod = viewModel::selectPeriod, onClose = onClose, onImport = onImport)
}

@Composable
fun RecapScreen(
    state: RecapUiState,
    onSelectPeriod: (RecapPeriod) -> Unit,
    onClose: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = periodTitle(state)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.feature_recap_close)) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(RecapPeriod.entries) { period ->
                    FilterChip(
                        selected = period == state.period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(stringResource(period.labelRes())) },
                    )
                }
            }
        }
        val recap = state.recap
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            when {
                recap == null -> Unit
                recap.isEmpty -> EmptyState(
                    title = stringResource(R.string.feature_recap_empty_title),
                    message = stringResource(R.string.feature_recap_empty_message),
                    actionLabel = stringResource(R.string.feature_recap_import),
                    onAction = onImport,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Stories(recap, title, key = state.period)
            }
        }
    }
}

@Composable
private fun periodTitle(state: RecapUiState): String = when {
    state.periodLabelMonth != null -> state.periodLabelMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + state.periodLabelMonth.year
    state.periodLabelYear != 0 -> state.periodLabelYear.toString()
    else -> stringResource(R.string.feature_recap_period_last_30)
}

/**
 * Instagram-style stories: auto-advancing pages, segmented progress bar, tap left/right
 * to navigate, press and hold to pause, and a 3D cube transition between pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Stories(recap: Recap, periodTitle: String, key: Any) {
    val pages = remember(recap) { buildPages(recap) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    var paused by remember { mutableStateOf(false) }
    val segment = remember(key) { Animatable(0f) }

    LaunchedEffect(key) { pager.scrollToPage(0) }
    LaunchedEffect(pager.currentPage, paused, key) {
        if (paused) return@LaunchedEffect
        if (pager.currentPage != pager.settledPage) segment.snapTo(0f)
        segment.animateTo(1f, tween(((1f - segment.value) * PAGE_DURATION_MS).toInt(), easing = LinearEasing))
        if (pager.currentPage < pages.lastIndex) {
            segment.snapTo(0f)
            if (reduce) pager.scrollToPage(pager.currentPage + 1) else pager.animateScrollToPage(pager.currentPage + 1)
        }
    }

    val colors = gradientFor(periodTitle + pager.currentPage)
    AnimatedGradientBackground(
        Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge),
        colors =
        colors + MusicManiaTheme.extendedColors.brandGradient.take(1),
        intensity = 0.55f,
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pages.size) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { offset ->
                            scope.launch {
                                segment.snapTo(0f)
                                val target = if (offset.x < size.width * BACK_ZONE) pager.currentPage - 1 else pager.currentPage + 1
                                pager.animateScrollToPage(target.coerceIn(0, pages.lastIndex))
                            }
                        },
                    )
                },
        ) { page ->
            Box(Modifier.fillMaxSize().cubeTransition(pager, page).padding(24.dp)) {
                StoryPage(pages[page], recap, periodTitle, isCurrent = pager.currentPage == page)
            }
        }
        ProgressSegments(pages.size, pager.currentPage, segment.value, Modifier.align(Alignment.TopCenter).padding(12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.cubeTransition(pager: PagerState, page: Int) = graphicsLayer {
    val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
    cameraDistance = CAMERA_DISTANCE * density
    rotationY = offset * -CUBE_ANGLE
    transformOrigin = TransformOrigin(if (offset < 0) 0f else 1f, PIVOT_CENTER)
    alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * FADE
}

@Composable
private fun ProgressSegments(count: Int, current: Int, progress: Float, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { index ->
            val fill = when {
                index < current -> 1f
                index == current -> progress
                else -> 0f
            }
            Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
            }
        }
    }
}

private fun RecapPeriod.labelRes(): Int = when (this) {
    RecapPeriod.LAST_30_DAYS -> R.string.feature_recap_period_last_30
    RecapPeriod.THIS_MONTH -> R.string.feature_recap_period_this_month
    RecapPeriod.LAST_MONTH -> R.string.feature_recap_period_last_month
    RecapPeriod.THIS_YEAR -> R.string.feature_recap_period_this_year
    RecapPeriod.LAST_YEAR -> R.string.feature_recap_period_last_year
}

private const val PAGE_DURATION_MS = 5_500
private const val BACK_ZONE = 0.3f
private const val CAMERA_DISTANCE = 12f
private const val CUBE_ANGLE = 70f
private const val FADE = 0.4f
private const val PIVOT_CENTER = 0.5f
