/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.chart.DonutChart
import io.github.tb18003.musicmania.core.designsystem.chart.HeatmapChart
import io.github.tb18003.musicmania.core.designsystem.chart.RadialClockChart
import io.github.tb18003.musicmania.core.designsystem.chart.RingProgress
import io.github.tb18003.musicmania.core.designsystem.component.AnimatedCounter
import io.github.tb18003.musicmania.core.designsystem.component.DeltaChip
import io.github.tb18003.musicmania.core.designsystem.component.staggeredEntrance
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.designsystem.theme.StatNumberStyle
import io.github.tb18003.musicmania.core.domain.stats.MilestoneCalculator
import io.github.tb18003.musicmania.core.domain.usecase.Insights
import io.github.tb18003.musicmania.core.model.Milestone
import io.github.tb18003.musicmania.core.model.MilestoneType
import io.github.tb18003.musicmania.core.model.PlaybackHabits
import io.github.tb18003.musicmania.core.ui.EmptyState
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.TimeRangeSelector
import io.github.tb18003.musicmania.core.ui.titleRes
import kotlin.math.roundToInt

@Composable
fun InsightsRoute(onImport: () -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsScreen(state, onSelectRange = viewModel::selectRange, onImport = onImport)
}

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onSelectRange: (io.github.tb18003.musicmania.core.model.TimeRange) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp)) {
                Text(stringResource(R.string.feature_insights_title), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                TimeRangeSelector(selected = state.range, onSelect = onSelectRange)
            }
        }
        val insights = state.insights
        when {
            insights == null -> Unit
            state.isEmpty -> item {
                EmptyState(
                    title = stringResource(R.string.feature_insights_empty_title),
                    message = stringResource(R.string.feature_insights_empty_message),
                    actionLabel = stringResource(R.string.feature_insights_import),
                    onAction = onImport,
                )
            }
            else -> {
                item { PersonalityCard(insights, Modifier.staggeredEntrance(0)) }
                item { HeatmapCard(state.heatmap, Modifier.staggeredEntrance(1)) }
                item { StreakAndCompareRow(insights, Modifier.staggeredEntrance(2)) }
                item { HabitsCard(insights.habits, Modifier.staggeredEntrance(3)) }
                item { MilestonesSection(insights.milestones, Modifier.staggeredEntrance(4)) }
            }
        }
    }
}

@Composable
private fun InsightCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PersonalityCard(insights: Insights, modifier: Modifier = Modifier) {
    InsightCard(stringResource(R.string.feature_insights_clock), modifier.padding(top = 12.dp)) {
        insights.personality?.let {
            Text(stringResource(R.string.feature_insights_you_are), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(it.titleRes()), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        }
        insights.clock.peakHour?.let { hour ->
            Text(
                stringResource(R.string.feature_insights_peak_hour, "%02d:00".format(hour)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        RadialClockChart(
            msByHour = insights.clock.msByHour,
            description = stringResource(R.string.feature_insights_clock_description, insights.clock.peakHour ?: 0),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun HeatmapCard(grid: List<List<Long>>, modifier: Modifier = Modifier) {
    val days = stringResource(R.string.feature_insights_weekday_initials).split(",")
    InsightCard(stringResource(R.string.feature_insights_heatmap), modifier) {
        HeatmapChart(
            cells = grid,
            rowLabels = days,
            description = stringResource(R.string.feature_insights_heatmap),
            modifier = Modifier.fillMaxWidth().aspectRatio(HEATMAP_ASPECT),
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)) {
            listOf("0", "6", "12", "18", "23").forEachIndexed { i, label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (i < 4) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StreakAndCompareRow(insights: Insights, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp)) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.tertiary)
                AnimatedCounter(insights.streaks.current.toLong(), style = StatNumberStyle)
                Text(stringResource(R.string.feature_insights_current_streak), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.feature_insights_longest_streak, insights.streaks.longest),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.feature_insights_vs_previous),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AnimatedCounter(
                    insights.comparison.current.minutes,
                    style = StatNumberStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                )
                Text(stringResource(R.string.feature_insights_minutes), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                insights.comparison.listenedChange?.let { DeltaChip(it) }
            }
        }
    }
}

@Composable
private fun HabitsCard(habits: PlaybackHabits, modifier: Modifier = Modifier) {
    InsightCard(stringResource(R.string.feature_insights_habits), modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            habits.skipRate?.let { RateRing(stringResource(R.string.feature_insights_skip_rate), it, Modifier.weight(1f)) }
            habits.shuffleRate?.let { RateRing(stringResource(R.string.feature_insights_shuffle_rate), it, Modifier.weight(1f)) }
        }
        if (habits.platforms.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.feature_insights_platforms), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            val top = habits.platforms.take(MAX_PLATFORMS)
            val total = top.sumOf { it.listenedMs }.coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(top.map { it.listenedMs.toFloat() }, Modifier.size(110.dp), description = top.joinToString { it.platform })
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    top.forEachIndexed { i, share ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape).background(
                                    MusicManiaTheme.extendedColors.chart[
                                        i %
                                            MusicManiaTheme.extendedColors.chart.size,
                                    ],
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${share.platform} · ${(share.listenedMs * PERCENT / total)}%",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        if (habits.skipRate == null && habits.shuffleRate == null && habits.platforms.isEmpty()) {
            Text(
                stringResource(R.string.feature_insights_habits_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RateRing(label: String, rate: Double, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            RingProgress(rate.toFloat(), Modifier.size(96.dp))
            Text("${(rate * PERCENT).roundToInt()}%", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MilestonesSection(milestones: List<Milestone>, modifier: Modifier = Modifier) {
    val next = MilestoneCalculator.nextGoals(milestones)
    val achieved = milestones.filter { it.achieved }
    Column(modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.feature_insights_milestones),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(next + achieved.reversed()) { milestone -> MilestoneBadge(milestone) }
        }
    }
}

@Composable
private fun MilestoneBadge(milestone: Milestone) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(140.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                RingProgress(milestone.progress, Modifier.size(72.dp), strokeWidthDp = 6f)
                Icon(
                    if (milestone.achieved) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                    null,
                    tint = if (milestone.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(Formatters.compact(milestone.threshold), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(milestone.type.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun MilestoneType.labelRes(): Int = when (this) {
    MilestoneType.MINUTES -> R.string.feature_insights_milestone_minutes
    MilestoneType.STREAMS -> R.string.feature_insights_milestone_streams
    MilestoneType.ARTISTS -> R.string.feature_insights_milestone_artists
    MilestoneType.STREAK_DAYS -> R.string.feature_insights_milestone_streak
}

private const val HEATMAP_ASPECT = 3.3f
private const val MAX_PLATFORMS = 5
private const val PERCENT = 100
