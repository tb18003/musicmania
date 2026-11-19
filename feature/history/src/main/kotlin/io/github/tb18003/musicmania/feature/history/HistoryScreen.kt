/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.history

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.SyncStatus
import io.github.tb18003.musicmania.core.ui.EmptyState
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.MediaRow
import io.github.tb18003.musicmania.core.ui.MediaRowSkeleton
import io.github.tb18003.musicmania.core.ui.SharedKeys
import java.time.LocalDate

@Composable
fun HistoryRoute(onPlayClick: (PlayEvent) -> Unit, onImport: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val status by viewModel.syncStatus.collectAsStateWithLifecycle()
    HistoryScreen(items = items, status = status, onSync = viewModel::syncNow, onPlayClick = onPlayClick, onImport = onImport)
}

@Composable
fun HistoryScreen(
    items: LazyPagingItems<HistoryItem>,
    status: SyncStatus,
    onSync: () -> Unit,
    onPlayClick: (PlayEvent) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_history_title), style = MaterialTheme.typography.displaySmall)
                Text(
                    status.lastSuccessfulSync?.let { stringResource(R.string.feature_history_last_sync, Formatters.relative(it)) }
                        ?: stringResource(R.string.feature_history_never_synced),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SyncButton(status.isRunning, onSync)
        }
        val refreshing = items.loadState.refresh is LoadState.Loading
        when {
            refreshing && items.itemCount == 0 -> LazyColumn { items(SKELETONS) { MediaRowSkeleton() } }
            items.itemCount == 0 -> EmptyState(
                title = stringResource(R.string.feature_history_empty_title),
                message = stringResource(R.string.feature_history_empty_message),
                icon = Icons.Rounded.History,
                actionLabel = stringResource(R.string.feature_history_import),
                onAction = onImport,
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { item ->
                        when (item) {
                            is HistoryItem.DayHeader -> "day-${item.date}"
                            is HistoryItem.Play -> "play-${item.event.id}-${item.event.playedAt}"
                        }
                    },
                    contentType = { index -> if (items.peek(index) is HistoryItem.DayHeader) "header" else "play" },
                ) { index ->
                    when (val item = items[index]) {
                        is HistoryItem.DayHeader -> DayHeader(item.date)
                        is HistoryItem.Play -> PlayRow(item.event, onPlayClick)
                        null -> MediaRowSkeleton()
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncButton(running: Boolean, onSync: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
        initialValue = 0f,
        targetValue = if (running && !reduce) FULL_TURN else 0f,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
        label = "rotation",
    )
    IconButton(onClick = onSync, enabled = !running) {
        Icon(
            Icons.Rounded.Sync,
            contentDescription = stringResource(R.string.feature_history_sync_now),
            modifier = Modifier.graphicsLayer { rotationZ = -rotation },
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> stringResource(R.string.feature_history_today)
        today.minusDays(1) -> stringResource(R.string.feature_history_yesterday)
        else -> Formatters.date(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlayRow(event: PlayEvent, onClick: (PlayEvent) -> Unit) {
    MediaRow(
        title = event.trackName,
        subtitle = event.artistName,
        imageUrl = event.imageUrl,
        trailing = Formatters.time(event.playedAt),
        artworkSize = 44.dp,
        sharedKey = SharedKeys.artwork("track", event.trackName, event.artistName),
        onClick = { onClick(event) },
    )
}

private const val SKELETONS = 10
private const val FULL_TURN = 360f
private const val SPIN_MS = 900
