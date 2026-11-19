/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.recap

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.tb18003.musicmania.core.designsystem.chart.RadialClockChart
import io.github.tb18003.musicmania.core.designsystem.component.AnimatedCounter
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.staggeredEntrance
import io.github.tb18003.musicmania.core.designsystem.theme.DisplayFontFamily
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.designsystem.theme.StatNumberStyle
import io.github.tb18003.musicmania.core.model.Recap
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.titleRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal enum class StoryPageType { INTRO, MINUTES, TOP_ARTIST, TOP_TRACKS, PERSONALITY, DISCOVERIES, SHARE }

internal fun buildPages(recap: Recap): List<StoryPageType> = buildList {
    add(StoryPageType.INTRO)
    add(StoryPageType.MINUTES)
    if (recap.topArtists.isNotEmpty()) add(StoryPageType.TOP_ARTIST)
    if (recap.topTracks.isNotEmpty()) add(StoryPageType.TOP_TRACKS)
    if (recap.personality != null) add(StoryPageType.PERSONALITY)
    add(StoryPageType.DISCOVERIES)
    add(StoryPageType.SHARE)
}

private val OnStory = Color.White

@Composable
internal fun StoryPage(type: StoryPageType, recap: Recap, periodTitle: String, isCurrent: Boolean) {
    // Re-run entrance animations every time a page becomes visible.
    if (!isCurrent) return
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        when (type) {
            StoryPageType.INTRO -> {
                Kicker(stringResource(R.string.feature_recap_intro_kicker))
                PopIn { Text(periodTitle, style = MaterialTheme.typography.displayLarge, color = OnStory) }
                Spacer(Modifier.height(12.dp))
                Body(stringResource(R.string.feature_recap_intro_body))
            }
            StoryPageType.MINUTES -> {
                Kicker(stringResource(R.string.feature_recap_minutes_kicker))
                AnimatedCounter(recap.summary.minutes, style = StatNumberStyle.copy(fontSize = 72.sp), color = OnStory)
                Body(stringResource(R.string.feature_recap_minutes_unit))
                Spacer(Modifier.height(20.dp))
                Body(stringResource(R.string.feature_recap_minutes_days, recap.summary.minutes / MINUTES_PER_DAY.toDouble()))
                Body(
                    stringResource(
                        R.string.feature_recap_streams,
                        Formatters.number(recap.summary.streams.toLong()),
                        Formatters.number(recap.summary.uniqueTracks.toLong()),
                    ),
                )
            }
            StoryPageType.TOP_ARTIST -> {
                val artist = recap.topArtists.first()
                Kicker(stringResource(R.string.feature_recap_top_artist_kicker))
                PopIn {
                    Artwork(
                        artist.imageUrl,
                        artist.artistName,
                        shape = CircleShape,
                        modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    artist.artistName,
                    style = MaterialTheme.typography.displaySmall,
                    color = OnStory,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Body(
                    stringResource(R.string.feature_recap_top_artist_body, artist.streams, artist.listenedMs / MS_PER_MINUTE),
                    Modifier.fillMaxWidth(),
                    TextAlign.Center,
                )
            }
            StoryPageType.TOP_TRACKS -> {
                Kicker(stringResource(R.string.feature_recap_top_tracks_kicker))
                recap.topTracks.forEachIndexed { index, track ->
                    Row(Modifier.padding(vertical = 8.dp).staggeredEntrance(index + 1), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}",
                            fontFamily = DisplayFontFamily,
                            style = MaterialTheme.typography.headlineMedium,
                            color = OnStory,
                            modifier = Modifier.width(40.dp),
                        )
                        Artwork(track.imageUrl, track.trackName, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                track.trackName,
                                style = MaterialTheme.typography.titleMedium,
                                color = OnStory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnStory.copy(alpha = 0.75f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            StoryPageType.PERSONALITY -> {
                Kicker(stringResource(R.string.feature_recap_personality_kicker))
                Text(stringResource(recap.personality!!.titleRes()), style = MaterialTheme.typography.displaySmall, color = OnStory)
                Spacer(Modifier.height(24.dp))
                RadialClockChart(recap.clock.msByHour, Modifier.fillMaxWidth().padding(horizontal = 32.dp))
            }
            StoryPageType.DISCOVERIES -> {
                Kicker(stringResource(R.string.feature_recap_discoveries_kicker))
                AnimatedCounter(recap.newArtists.toLong(), style = StatNumberStyle.copy(fontSize = 72.sp), color = OnStory)
                Body(stringResource(R.string.feature_recap_new_artists))
                Spacer(Modifier.height(28.dp))
                AnimatedCounter(recap.longestStreak.toLong(), style = StatNumberStyle, color = OnStory)
                Body(stringResource(R.string.feature_recap_streak))
            }
            StoryPageType.SHARE -> SharePage(recap, periodTitle)
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = OnStory.copy(alpha = 0.8f),
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = OnStory.copy(alpha = 0.9f), modifier = modifier, textAlign = align)
}

/** Springy scale-in for hero elements. */
@Composable
private fun PopIn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduce) 1f else POP_START) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    val alpha = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(POP_FADE_MS)) }
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
        content = content,
    )
}

@Composable
private fun SharePage(recap: Recap, periodTitle: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.drawWithContent {
                // Record the card into a GraphicsLayer so it can be exported as a bitmap.
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) { ShareCard(recap, periodTitle) }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { scope.launch { shareBitmap(context, layer.toImageBitmap().asAndroidBitmap()) } },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Icon(Icons.Rounded.Share, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.feature_recap_share))
        }
    }
}

@Composable
private fun ShareCard(recap: Recap, periodTitle: String) {
    Column(
        Modifier
            .width(300.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(MusicManiaTheme.extendedColors.brandGradient))
            .padding(24.dp),
    ) {
        Text("MusicMania · $periodTitle", style = MaterialTheme.typography.labelLarge, color = Color.Black.copy(alpha = 0.7f))
        Spacer(Modifier.height(12.dp))
        Text("${recap.summary.minutes}", style = StatNumberStyle, color = Color.Black)
        Text(stringResource(R.string.feature_recap_minutes_unit), style = MaterialTheme.typography.titleMedium, color = Color.Black)
        Spacer(Modifier.height(16.dp))
        recap.topArtists.firstOrNull()?.let {
            Text(
                stringResource(R.string.feature_recap_top_artist_kicker),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black.copy(alpha = 0.7f),
            )
            Text(it.artistName, style = MaterialTheme.typography.titleLarge, color = Color.Black)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            stringResource(R.string.feature_recap_top_tracks_kicker),
            style = MaterialTheme.typography.labelMedium,
            color = Color.Black.copy(alpha = 0.7f),
        )
        recap.topTracks.take(SHARE_TRACKS).forEachIndexed { i, t ->
            Text(
                "${i + 1}. ${t.trackName} — ${t.artistName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Writes the PNG into the app cache (`cache/shared`, exposed read-only via FileProvider)
 * and opens the system share sheet. No storage permission is required.
 */
private suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "musicmania_recap.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: IOException) {
            null
        }
    } ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        // No app can handle the share intent.
    }
}

private const val MINUTES_PER_DAY = 1_440
private const val MS_PER_MINUTE = 60_000L
private const val POP_START = 0.6f
private const val POP_FADE_MS = 400
private const val SHARE_TRACKS = 3
private const val PNG_QUALITY = 100
