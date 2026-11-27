/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.leaderboard

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.SegmentedSelector
import io.github.tb18003.musicmania.core.designsystem.component.staggeredEntrance
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.ui.EmptyState
import io.github.tb18003.musicmania.core.ui.ErrorState
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.MediaRowSkeleton
import kotlinx.coroutines.launch

@Composable
fun LeaderboardRoute(modifier: Modifier = Modifier, viewModel: LeaderboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LeaderboardScreen(
        state = state,
        actions = LeaderboardActions(
            onMetric = viewModel::selectMetric,
            onPeriod = viewModel::selectPeriod,
            onEnableSharing = viewModel::enableSharing,
            onFriendClick = viewModel::openFriend,
            onFriendDismiss = viewModel::closeFriend,
            onRetry = viewModel::retry,
        ),
        modifier = modifier,
    )
}

data class LeaderboardActions(
    val onMetric: (LeaderboardMetric) -> Unit = {},
    val onPeriod: (LeaderboardPeriod) -> Unit = {},
    val onEnableSharing: () -> Unit = {},
    val onFriendClick: (String) -> Unit = {},
    val onFriendDismiss: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun LeaderboardScreen(state: LeaderboardUiState, actions: LeaderboardActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(stringResource(R.string.feature_leaderboard_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.feature_leaderboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.content is LeaderboardContent.Ranking || state.content is LeaderboardContent.Loading) {
            Selectors(state, actions)
        }
        when (val content = state.content) {
            LeaderboardContent.Loading -> LazyColumn { items(SKELETON_ROWS) { MediaRowSkeleton() } }
            is LeaderboardContent.NotAllowlisted -> NotAllowlisted(content.spotifyUserId)
            LeaderboardContent.SharingOff -> EmptyState(
                title = stringResource(R.string.feature_leaderboard_sharing_off_title),
                message = stringResource(R.string.feature_leaderboard_sharing_off_message),
                icon = Icons.Rounded.Groups,
                actionLabel = stringResource(R.string.feature_leaderboard_enable_sharing).takeUnless { state.isEnablingSharing },
                onAction = actions.onEnableSharing,
            )
            is LeaderboardContent.Error -> ErrorState(content.error, onRetry = actions.onRetry)
            is LeaderboardContent.Ranking -> if (content.entries.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.feature_leaderboard_empty_title),
                    message = stringResource(R.string.feature_leaderboard_empty_message),
                    icon = Icons.Rounded.EmojiEvents,
                )
            } else {
                Ranking(content.entries, state, actions.onFriendClick)
            }
        }
    }
    state.selected?.let { selected ->
        val entry = (state.content as? LeaderboardContent.Ranking)?.entries?.firstOrNull { it.spotifyUserId == selected.spotifyUserId }
        FriendDialog(selected, entry, state.metric, actions.onFriendDismiss)
    }
}

@Composable
private fun Selectors(state: LeaderboardUiState, actions: LeaderboardActions) {
    val periods = LeaderboardPeriod.entries
    val metrics = LeaderboardMetric.entries
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedSelector(
            options = periods.map { stringResource(it.label) },
            selectedIndex = periods.indexOf(state.period),
            onSelect = { actions.onPeriod(periods[it]) },
        )
        SegmentedSelector(
            options = metrics.map { stringResource(it.label) },
            selectedIndex = metrics.indexOf(state.metric),
            onSelect = { actions.onMetric(metrics[it]) },
        )
    }
}

@Composable
private fun Ranking(entries: List<LeaderboardEntry>, state: LeaderboardUiState, onFriendClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.spotifyUserId }) { index, entry ->
            RankingRow(
                entry = entry,
                metric = state.metric,
                onClick = { onFriendClick(entry.spotifyUserId) },
                modifier = Modifier.staggeredEntrance(index),
            )
        }
    }
}

@Composable
private fun RankingRow(entry: LeaderboardEntry, metric: LeaderboardMetric, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = entry.displayName ?: entry.spotifyUserId
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (entry.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.feature_leaderboard_details), onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(entry.rank)
            Spacer(Modifier.width(12.dp))
            Artwork(imageUrl = entry.avatarUrl, title = name, shape = CircleShape, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.isMe) {
                    Text(
                        stringResource(R.string.feature_leaderboard_you),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(formatValue(metric, entry.value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val podium = when (rank) {
        1 -> Color(GOLD)
        2 -> Color(SILVER)
        3 -> Color(BRONZE)
        else -> null
    }
    Surface(shape = CircleShape, color = podium ?: MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(32.dp)) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (podium != null) Color.Black else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NotAllowlisted(spotifyUserId: String?) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            title = stringResource(R.string.feature_leaderboard_not_allowlisted_title),
            message = stringResource(R.string.feature_leaderboard_not_allowlisted_message),
            icon = Icons.Rounded.Lock,
        )
        if (spotifyUserId != null) {
            Text(
                spotifyUserId,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedButton(
                onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Spotify ID", spotifyUserId))) } },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.feature_leaderboard_copy_id))
            }
        }
    }
}

@Composable
internal fun formatValue(metric: LeaderboardMetric, value: Long): String = when (metric) {
    LeaderboardMetric.MINUTES -> stringResource(R.string.feature_leaderboard_minutes_value, Formatters.number(value))
    LeaderboardMetric.STREAK -> pluralStringResource(R.plurals.feature_leaderboard_days, value.toInt(), value.toInt())
    LeaderboardMetric.STREAMS, LeaderboardMetric.UNIQUE_ARTISTS -> Formatters.number(value)
}

private val LeaderboardPeriod.label: Int
    get() = when (this) {
        LeaderboardPeriod.WEEK -> R.string.feature_leaderboard_period_week
        LeaderboardPeriod.MONTH -> R.string.feature_leaderboard_period_month
        LeaderboardPeriod.YEAR -> R.string.feature_leaderboard_period_year
        LeaderboardPeriod.ALL_TIME -> R.string.feature_leaderboard_period_all_time
    }

private val LeaderboardMetric.label: Int
    get() = when (this) {
        LeaderboardMetric.MINUTES -> R.string.feature_leaderboard_metric_minutes
        LeaderboardMetric.STREAMS -> R.string.feature_leaderboard_metric_streams
        LeaderboardMetric.STREAK -> R.string.feature_leaderboard_metric_streak
        LeaderboardMetric.UNIQUE_ARTISTS -> R.string.feature_leaderboard_metric_artists
    }

private const val SKELETON_ROWS = 6
private const val GOLD = 0xFFFFD54F
private const val SILVER = 0xFFCFD8DC
private const val BRONZE = 0xFFD7A26B

@Preview
@Composable
private fun LeaderboardPreview() {
    MusicManiaTheme(darkTheme = true) {
        LeaderboardScreen(
            state = LeaderboardUiState(
                content = LeaderboardContent.Ranking(
                    listOf(
                        LeaderboardEntry(1, "ana", "Ana", null, 1510, isMe = false),
                        LeaderboardEntry(2, "me", "You", null, 1240, isMe = true),
                        LeaderboardEntry(2, "carla", "Carla", null, 1240, isMe = false),
                    ),
                ),
            ),
            actions = LeaderboardActions(),
        )
    }
}
