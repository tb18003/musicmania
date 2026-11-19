/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.chart.BarChart
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.StatTile
import io.github.tb18003.musicmania.core.designsystem.component.staggeredEntrance
import io.github.tb18003.musicmania.core.model.ItemStats
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.SharedKeys
import io.github.tb18003.musicmania.core.ui.rememberArtworkColors
import io.github.tb18003.musicmania.core.ui.sharedElementOrSelf

@Composable
fun DetailRoute(onBack: () -> Unit, viewModel: DetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailScreen(state, onBack)
}

@Composable
fun DetailScreen(state: DetailUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberArtworkColors(state.imageUrl, state.title)
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
        // Palette-tinted backdrop that fades out as the header scrolls away
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .graphicsLayer {
                    alpha = if (listState.firstVisibleItemIndex > 0) 0f else 1f - listState.firstVisibleItemScrollOffset / FADE_DISTANCE
                }
                .background(Brush.verticalGradient(listOf(colors.first().copy(alpha = 0.55f), Color.Transparent))),
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Header(state, listState.firstVisibleItemScrollOffset) }
            item { StatsGrid(state.stats) }
            state.stats?.monthly?.takeIf { it.size > 1 }?.let { monthly ->
                item {
                    Column(Modifier.padding(20.dp).staggeredEntrance(2)) {
                        Text(stringResource(R.string.feature_detail_monthly), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        val recent = monthly.takeLast(MONTHS_SHOWN)
                        BarChart(
                            values = recent.map { it.streams.toFloat() },
                            highlightIndex = recent.indices.maxByOrNull { recent[it].streams },
                            description = stringResource(R.string.feature_detail_monthly),
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(
                                recent.first().month.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                recent.last().month.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.genres.isNotEmpty()) item { Genres(state.genres) }
            state.spotifyUri?.let { uri -> item { OpenInSpotify(uri) } }
        }
        FilledTonalIconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_detail_back))
        }
    }
}

@Composable
private fun Header(state: DetailUiState, scrollOffset: Int) {
    val circular = state.kind == DetailKind.ARTIST
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 56.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            imageUrl = state.imageUrl,
            title = state.title,
            shape = if (circular) CircleShape else MaterialTheme.shapes.large,
            modifier = Modifier
                .size(220.dp)
                .sharedElementOrSelf(SharedKeys.artwork(state.kind.key, state.title, state.sharedKeyArtist))
                .graphicsLayer {
                    // Gentle parallax + shrink while scrolling
                    translationY = scrollOffset * PARALLAX
                    val scale = (1f - scrollOffset / SHRINK_DISTANCE).coerceIn(MIN_SCALE, 1f)
                    scaleX = scale
                    scaleY = scale
                },
        )
        Spacer(Modifier.height(20.dp))
        Text(state.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (state.subtitle.isNotBlank() && state.kind != DetailKind.ARTIST) {
            Text(
                state.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        state.releaseYear?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsGrid(stats: ItemStats?) {
    Column(
        Modifier.padding(horizontal = 20.dp).padding(top = 24.dp).staggeredEntrance(1),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (stats == null || stats.streams == 0) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.feature_detail_no_plays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(R.string.feature_detail_streams), stats.streams.toLong(), Modifier.weight(1f))
            StatTile(stringResource(R.string.feature_detail_minutes), Formatters.minutes(stats.listenedMs), Modifier.weight(1f))
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(20.dp)) {
                DateStat(
                    stringResource(R.string.feature_detail_first_listen),
                    stats.firstPlayed?.let {
                        Formatters.date(it)
                    },
                    Modifier.weight(1f),
                )
                DateStat(
                    stringResource(R.string.feature_detail_last_listen),
                    stats.lastPlayed?.let {
                        Formatters.date(it)
                    },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DateStat(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Genres(genres: List<String>) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.feature_detail_genres), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            genres.forEach { AssistChip(onClick = {}, label = { Text(it.replaceFirstChar { c -> c.uppercase() }) }) }
        }
    }
}

/** Spotify's guidelines ask apps showing its content to link back to it. */
@Composable
private fun OpenInSpotify(uri: String) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            } catch (_: ActivityNotFoundException) {
                // Spotify not installed.
            }
        },
        modifier = Modifier.fillMaxWidth().padding(20.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.feature_detail_open_spotify))
    }
}

private const val FADE_DISTANCE = 900f
private const val PARALLAX = 0.35f
private const val SHRINK_DISTANCE = 1_400f
private const val MIN_SCALE = 0.75f
private const val MONTHS_SHOWN = 24
