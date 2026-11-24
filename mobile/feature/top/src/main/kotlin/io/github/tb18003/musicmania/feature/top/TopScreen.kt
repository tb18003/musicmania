/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.top

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.chart.rememberEntranceProgress
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.SegmentedSelector
import io.github.tb18003.musicmania.core.designsystem.component.bouncyClickable
import io.github.tb18003.musicmania.core.designsystem.component.staggeredEntrance
import io.github.tb18003.musicmania.core.designsystem.theme.DisplayFontFamily
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.Genre
import io.github.tb18003.musicmania.core.model.Ranked
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopAlbum
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.ui.EmptyState
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.MediaRow
import io.github.tb18003.musicmania.core.ui.MediaRowSkeleton
import io.github.tb18003.musicmania.core.ui.SharedKeys
import io.github.tb18003.musicmania.core.ui.TimeRangeSelector
import io.github.tb18003.musicmania.core.ui.labelRes
import io.github.tb18003.musicmania.core.ui.messageRes
import io.github.tb18003.musicmania.core.ui.sharedElementOrSelf

data class TopActions(
    val onTrackClick: (Track) -> Unit = {},
    val onArtistClick: (Artist) -> Unit = {},
    val onAlbumClick: (TopAlbum) -> Unit = {},
)

@Composable
fun TopRoute(actions: TopActions, snackbarHostState: SnackbarHostState, viewModel: TopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var showDialog by rememberSaveable { mutableStateOf(false) }

    // Single collector: one-shot events from a Channel must not be fanned out.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TopEvent.PlaylistCreated -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.feature_top_playlist_created),
                        actionLabel = resources.getString(R.string.feature_top_open),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) openSpotifyUri(context, event.uri)
                }
                is TopEvent.Error -> snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()))
            }
        }
    }

    TopScreen(
        state = state,
        actions = actions,
        onSelectRange = viewModel::selectRange,
        onSelectTab = viewModel::selectTab,
        onCreatePlaylist = { showDialog = true },
    )

    if (showDialog) {
        CreatePlaylistDialog(
            defaultName = stringResource(R.string.feature_top_playlist_default_name, stringResource(state.range.labelRes())),
            description = stringResource(R.string.feature_top_playlist_description),
            onDismiss = { showDialog = false },
            onConfirm = { name, description ->
                showDialog = false
                viewModel.createPlaylist(name, description)
            },
        )
    }
}

private fun openSpotifyUri(context: android.content.Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Spotify is not installed: nothing to open.
    }
}

@Composable
fun TopScreen(
    state: TopUiState,
    actions: TopActions,
    onSelectRange: (TimeRange) -> Unit,
    onSelectTab: (TopTab) -> Unit,
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp)) {
                Text(stringResource(R.string.feature_top_title), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                TimeRangeSelector(selected = state.range, onSelect = onSelectRange)
                Spacer(Modifier.height(10.dp))
                SegmentedSelector(
                    options = TopTab.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = state.tab.ordinal,
                    onSelect = { onSelectTab(TopTab.entries[it]) },
                )
            }
            // Animate between (tab, range) selections; each side renders its own content.
            AnimatedContent(
                targetState = state,
                contentKey = { it.tab to it.range },
                transitionSpec = {
                    val forward = targetState.tab.ordinal >= initialState.tab.ordinal
                    (fadeIn() + slideInHorizontally { if (forward) it / 6 else -it / 6 }) togetherWith
                        (fadeOut() + slideOutHorizontally { if (forward) -it / 6 else it / 6 })
                },
                label = "topContent",
                modifier = Modifier.weight(1f),
            ) { target ->
                TopContentList(target.content, actions)
            }
        }
        AnimatedVisibility(
            visible = state.canCreatePlaylist && state.content is TopContent.Tracks && !state.content.isEmpty,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = onCreatePlaylist,
                icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                text = { Text(stringResource(R.string.feature_top_create_playlist)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun TopContentList(content: TopContent, actions: TopActions) {
    val listState = rememberLazyListState()
    when {
        content is TopContent.Loading -> LazyColumn(Modifier.fillMaxSize()) { items(SKELETONS) { MediaRowSkeleton() } }
        content.isEmpty -> EmptyState(
            title = stringResource(R.string.feature_top_empty_title),
            message = stringResource(R.string.feature_top_empty_message),
        )
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (content) {
                is TopContent.Tracks -> rankedItems(content.items, listState) { ranked, hero ->
                    val track = ranked.item
                    RankedEntry(
                        ranked,
                        track.name,
                        track.artistNames,
                        track.album.imageUrl,
                        "track",
                        track.primaryArtistName,
                        hero,
                        false,
                    ) {
                        actions.onTrackClick(track)
                    }
                }
                is TopContent.Artists -> rankedItems(content.items, listState) { ranked, hero ->
                    val artist = ranked.item
                    val subtitle = artist.genres.take(2).joinToString(" · ").ifEmpty { ranked.streams?.let { "$it ▶" } ?: "" }
                    RankedEntry(ranked, artist.name, subtitle, artist.imageUrl, "artist", "", hero, true) { actions.onArtistClick(artist) }
                }
                is TopContent.Albums -> rankedItems(content.items, listState) { ranked, hero ->
                    val album = ranked.item
                    RankedEntry(ranked, album.album.name, album.artistName, album.album.imageUrl, "album", album.artistName, hero, false) {
                        actions.onAlbumClick(album)
                    }
                }
                is TopContent.Genres -> itemsIndexed(content.items, key = { _, g -> g.name }) { index, genre -> GenreRow(index, genre) }
                TopContent.Loading -> Unit
            }
        }
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.rankedItems(
    items: List<Ranked<T>>,
    listState: LazyListState,
    content: @Composable (Ranked<T>, HeroInfo?) -> Unit,
) {
    itemsIndexed(items, key = { _, r -> r.rank }) { index, ranked ->
        Box(Modifier.staggeredEntrance(index)) {
            content(ranked, if (index == 0) HeroInfo(listState) else null)
        }
    }
}

private class HeroInfo(val listState: LazyListState)

@Composable
private fun <T> RankedEntry(
    ranked: Ranked<T>,
    title: String,
    subtitle: String,
    imageUrl: String?,
    kind: String,
    artist: String,
    hero: HeroInfo?,
    circular: Boolean,
    onClick: () -> Unit,
) {
    val sharedKey = SharedKeys.artwork(kind, title, artist)
    val trailing = ranked.streams?.let { Formatters.compact(it.toLong()) }
    if (hero != null) {
        HeroCard(title, subtitle, imageUrl, sharedKey, hero.listState, onClick)
    } else {
        MediaRow(
            title = title,
            subtitle = subtitle,
            imageUrl = imageUrl,
            rank = ranked.rank,
            movement = ranked.movement,
            trailing = trailing,
            circular = circular,
            sharedKey = sharedKey,
            onClick = onClick,
        )
    }
}

/** #1 item: large artwork with a parallax effect while the list scrolls. */
@Composable
private fun HeroCard(title: String, subtitle: String, imageUrl: String?, sharedKey: String, listState: LazyListState, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .height(240.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .bouncyClickable(onClick = onClick),
    ) {
        Artwork(
            imageUrl = imageUrl,
            title = title,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxSize()
                .sharedElementOrSelf(sharedKey)
                .graphicsLayer {
                    val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                    translationY = offset * PARALLAX
                    val zoom = 1f + offset / ZOOM_DIVISOR
                    scaleX = zoom
                    scaleY = zoom
                },
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(
                "#1",
                fontFamily = DisplayFontFamily,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(
                    CircleShape,
                ).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

@Composable
private fun GenreRow(index: Int, genre: Genre) {
    val progress = rememberEntranceProgress(genre, delayMillis = index * GENRE_STAGGER_MS)
    val colors = MusicManiaTheme.extendedColors.chart
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                fontFamily = DisplayFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
            Text(
                genre.name.replaceFirstChar {
                    it.uppercase()
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(genre.score * PERCENT).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(
                Modifier
                    .fillMaxWidth((genre.score * progress).toFloat().coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(colors[index % colors.size], colors[(index + 1) % colors.size]))),
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(defaultName: String, description: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_top_create_playlist)) },
        text = {
            Column {
                Text(stringResource(R.string.feature_top_playlist_dialog_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = name, onValueChange = {
                    name = it.take(MAX_NAME)
                }, singleLine = true, label = { Text(stringResource(R.string.feature_top_playlist_name)) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name.ifBlank { defaultName }, description)
            }) { Text(stringResource(R.string.feature_top_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private fun TopTab.labelRes(): Int = when (this) {
    TopTab.TRACKS -> R.string.feature_top_tab_tracks
    TopTab.ARTISTS -> R.string.feature_top_tab_artists
    TopTab.ALBUMS -> R.string.feature_top_tab_albums
    TopTab.GENRES -> R.string.feature_top_tab_genres
}

private const val SKELETONS = 8
private const val PARALLAX = 0.45f
private const val ZOOM_DIVISOR = 2_000f
private const val GENRE_STAGGER_MS = 40
private const val PERCENT = 100
private const val MAX_NAME = 100
