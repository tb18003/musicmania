/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.RankedItem
import io.github.tb18003.musicmania.core.ui.Formatters

/** A friend's details with the covers of their top artists and tracks, over the ranking. */
@Composable
internal fun FriendDialog(selected: SelectedFriend, entry: LeaderboardEntry?, metric: LeaderboardMetric, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FriendDialogContent(selected, entry, metric, onDismiss, Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
    }
}

@Composable
internal fun FriendDialogContent(
    selected: SelectedFriend,
    entry: LeaderboardEntry?,
    metric: LeaderboardMetric,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = entry?.let { if (it.isMe) stringResource(R.string.feature_leaderboard_you) else it.displayName ?: it.spotifyUserId }
        ?: selected.stats?.displayName
        ?: selected.spotifyUserId
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.widthIn(max = MAX_WIDTH.dp).fillMaxWidth().semantics { paneTitle = name },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Header(name, entry, metric, onDismiss)
            Spacer(Modifier.height(16.dp))
            val stats = selected.stats
            when {
                selected.isLoading -> Box(Modifier.fillMaxWidth().height(LOADING_HEIGHT.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                stats == null -> Text(
                    stringResource(R.string.feature_leaderboard_friend_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> FriendDetails(stats)
            }
        }
    }
}

@Composable
private fun Header(name: String, entry: LeaderboardEntry?, metric: LeaderboardMetric, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Artwork(imageUrl = entry?.avatarUrl, title = name, shape = CircleShape, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (entry != null) {
                Text(
                    "#${entry.rank} · ${formatValue(metric, entry.value)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalIconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.feature_leaderboard_close))
        }
    }
}

@Composable
private fun FriendDetails(stats: FriendStats) {
    StatsRow(stats)
    if (stats.topArtists.isNotEmpty()) {
        SectionTitle(stringResource(R.string.feature_leaderboard_top_artists))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.topArtists.take(TOP_SHOWN).forEach { artist -> ArtistCell(artist, Modifier.weight(1f)) }
            // Keep the cells the same width when fewer than five artists are known.
            repeat(TOP_SHOWN - stats.topArtists.take(TOP_SHOWN).size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (stats.topTracks.isNotEmpty()) {
        SectionTitle(stringResource(R.string.feature_leaderboard_top_tracks))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.topTracks.take(TOP_SHOWN).forEachIndexed { index, track -> TrackRow(index + 1, track) }
        }
    }
    if (stats.topArtists.isEmpty() && stats.topTracks.isEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.feature_leaderboard_friend_no_top),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsRow(stats: FriendStats) {
    val cells = listOf(
        Formatters.number(stats.minutes) to stringResource(R.string.feature_leaderboard_metric_minutes),
        Formatters.number(stats.streams.toLong()) to stringResource(R.string.feature_leaderboard_metric_streams),
        Formatters.number(stats.uniqueArtists.toLong()) to stringResource(R.string.feature_leaderboard_metric_artists),
        pluralStringResource(R.plurals.feature_leaderboard_days, stats.currentStreak, stats.currentStreak) to
            stringResource(R.string.feature_leaderboard_metric_streak),
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 12.dp)) {
            cells.forEachIndexed { index, (value, label) ->
                if (index > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun ArtistCell(artist: RankedItem, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(imageUrl = artist.imageUrl, title = artist.name, shape = CircleShape, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackRow(position: Int, track: RankedItem) {
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(
            position.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Artwork(imageUrl = track.imageUrl, title = track.name, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Text(track.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val TOP_SHOWN = 5
private const val LOADING_HEIGHT = 160

/** On tablets and in landscape the card keeps a readable width instead of stretching. */
private const val MAX_WIDTH = 520

internal val previewFriend = FriendStats(
    spotifyUserId = "ana",
    displayName = "Ana",
    minutes = 1_510,
    streams = 520,
    uniqueArtists = 33,
    currentStreak = 30,
    topArtists = listOf("Coral Echo", "Nova Lights", "Velvet Static", "Paper Moons", "Glass Harbor").map(::RankedItem),
    topTracks = listOf("Low Tide", "Neon Rain", "Afterglow", "Static Hearts", "Orbit").map(::RankedItem),
)

@Preview
@Composable
private fun FriendDialogPreview() {
    MusicManiaTheme(darkTheme = true) {
        FriendDialogContent(
            selected = SelectedFriend("ana", previewFriend, isLoading = false),
            entry = LeaderboardEntry(1, "ana", "Ana", null, 1_510, isMe = false),
            metric = LeaderboardMetric.MINUTES,
            onDismiss = {},
        )
    }
}
