/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.chart.AreaChart
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.StatTile
import io.github.tb18003.musicmania.core.designsystem.component.bouncyClickable
import io.github.tb18003.musicmania.core.designsystem.component.staggeredEntrance
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.ui.MediaRow
import io.github.tb18003.musicmania.core.ui.MediaRowSkeleton
import io.github.tb18003.musicmania.core.ui.SectionHeader
import io.github.tb18003.musicmania.core.ui.SharedKeys
import io.github.tb18003.musicmania.core.ui.message
import io.github.tb18003.musicmania.core.ui.sharedElementOrSelf
import java.time.LocalTime

/** Navigation callbacks; the app module wires them to destinations in other features. */
data class HomeActions(
    val onTrackClick: (Track) -> Unit = {},
    val onArtistClick: (Artist) -> Unit = {},
    val onSeeAllTop: () -> Unit = {},
    val onOpenRecap: () -> Unit = {},
    val onOpenInsights: () -> Unit = {},
    val onOpenImport: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun HomeRoute(actions: HomeActions, snackbarHostState: SnackbarHostState, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val errorMessage = state.error?.message()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.dismissError()
        }
    }
    HomeScreen(state = state, nowPlaying = nowPlaying, actions = actions, onRefresh = viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, nowPlaying: NowPlaying?, actions: HomeActions, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item { Header(state, actions.onOpenSettings) }
            item {
                AnimatedVisibility(nowPlaying != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    nowPlaying?.let {
                        NowPlayingCard(it, onClick = { actions.onTrackClick(it.track) }, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
            item { StatsSection(state) }
            item { ShortcutsRow(state, actions) }
            if (state.topArtists.isNotEmpty()) {
                item {
                    SectionHeader(
                        stringResource(R.string.feature_home_top_artists),
                        Modifier.padding(start = 20.dp, top = 24.dp, end = 8.dp),
                    )
                }
                item { ArtistsCarousel(state.topArtists.map { it.item }, actions.onArtistClick) }
            }
            item {
                SectionHeader(
                    stringResource(R.string.feature_home_top_tracks),
                    Modifier.padding(start = 20.dp, top = 24.dp, end = 8.dp),
                    actionLabel = stringResource(R.string.feature_home_see_all),
                    onAction = actions.onSeeAllTop,
                )
            }
            if (state.isLoading) {
                items(PREVIEW_SKELETONS) { MediaRowSkeleton() }
            } else {
                itemsIndexed(state.topTracks, key = { _, ranked -> "track-${ranked.rank}-${ranked.item.name}" }) { index, ranked ->
                    val track = ranked.item
                    MediaRow(
                        title = track.name,
                        subtitle = track.artistNames,
                        imageUrl = track.album.imageUrl,
                        rank = ranked.rank,
                        movement = ranked.movement,
                        sharedKey = SharedKeys.artwork("track", track.name, track.primaryArtistName),
                        onClick = { actions.onTrackClick(track) },
                        modifier = Modifier.staggeredEntrance(index),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState, onOpenSettings: () -> Unit) {
    val greeting = when (LocalTime.now().hour) {
        in MORNING -> R.string.feature_home_greeting_morning
        in AFTERNOON -> R.string.feature_home_greeting_afternoon
        else -> R.string.feature_home_greeting_evening
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(greeting), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.profile?.displayName ?: stringResource(R.string.feature_home_default_name),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Artwork(
            imageUrl = state.profile?.imageUrl,
            title = state.profile?.displayName ?: "♪",
            shape = CircleShape,
            contentDescription = stringResource(R.string.feature_home_settings),
            modifier = Modifier
                .size(44.dp)
                .bouncyClickable(onClickLabel = stringResource(R.string.feature_home_settings), onClick = onOpenSettings),
        )
    }
}

@Composable
private fun StatsSection(state: HomeUiState) {
    val overview = state.overview ?: return
    val comparison = overview.comparison
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp)) {
        Text(
            stringResource(R.string.feature_home_last_four_weeks),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.feature_home_minutes),
                value = comparison.current.minutes,
                delta = comparison.listenedChange,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.feature_home_streams),
                value = comparison.current.streams.toLong(),
                modifier = Modifier.weight(1f),
            )
        }
        if (overview.daily.size > 1) {
            Spacer(Modifier.height(16.dp))
            AreaChart(
                values = overview.daily.map { it.listenedMs.toFloat() },
                description = stringResource(R.string.feature_home_daily_chart_description),
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
        }
    }
}

@Composable
private fun ShortcutsRow(state: HomeUiState, actions: HomeActions) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 20.dp),
    ) {
        item {
            ShortcutCard(
                stringResource(R.string.feature_home_recap_title),
                stringResource(R.string.feature_home_recap_body),
                Icons.Rounded.AutoAwesome,
                MusicManiaTheme.extendedColors.brandGradient,
                actions.onOpenRecap,
            )
        }
        item {
            ShortcutCard(
                stringResource(R.string.feature_home_insights_title),
                stringResource(R.string.feature_home_insights_body),
                Icons.Rounded.Insights,
                listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary),
                actions.onOpenInsights,
            )
        }
        if (state.overview?.hasHistory != true || state.overview.comparison.current.streams < FEW_STREAMS) {
            item {
                ShortcutCard(
                    stringResource(R.string.feature_home_import_title),
                    stringResource(R.string.feature_home_import_body),
                    Icons.Rounded.FileUpload,
                    listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary),
                    actions.onOpenImport,
                )
            }
        }
    }
}

@Composable
private fun ShortcutCard(title: String, body: String, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Column(
        Modifier
            .width(200.dp)
            .height(132.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.85f) }))
            .bouncyClickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, null, tint = Color(0xFF0A0A0F))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF0A0A0F), modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color(0xFF0A0A0F), modifier = Modifier.size(18.dp))
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = Color(0xCC0A0A0F), maxLines = 2)
    }
}

@Composable
private fun ArtistsCarousel(artists: List<Artist>, onArtistClick: (Artist) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(artists, key = { _, artist -> artist.id ?: artist.name }) { index, artist ->
            Column(
                Modifier
                    .width(96.dp)
                    .staggeredEntrance(index)
                    .bouncyClickable { onArtistClick(artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Artwork(
                        imageUrl = artist.imageUrl,
                        title = artist.name,
                        shape = CircleShape,
                        modifier = Modifier.size(96.dp).sharedElementOrSelf(SharedKeys.artwork("artist", artist.name)),
                    )
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    artist.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val MORNING = 5..11
private val AFTERNOON = 12..19
private const val PREVIEW_SKELETONS = 5
private const val FEW_STREAMS = 500

@Preview
@Composable
private fun HomePreview() {
    MusicManiaTheme(darkTheme = true) {
        HomeScreen(HomeUiState(isLoading = false), nowPlaying = null, actions = HomeActions(), onRefresh = {})
    }
}
