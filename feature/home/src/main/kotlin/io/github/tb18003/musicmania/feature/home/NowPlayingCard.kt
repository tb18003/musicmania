/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.EqualizerBars
import io.github.tb18003.musicmania.core.designsystem.component.bouncyClickable
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.rememberArtworkColors

/**
 * Live "now playing" card. The API is polled every ~10 s; in between, progress is
 * interpolated every frame so the bar moves smoothly. The card tints itself with
 * the artwork's palette and the artwork gently "breathes" while music plays.
 */
@Composable
internal fun NowPlayingCard(nowPlaying: NowPlaying, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val track = nowPlaying.track
    val colors = rememberArtworkColors(track.album.imageUrl, track.name)
    var progressMs by remember(nowPlaying) { mutableLongStateOf(nowPlaying.progressMs) }
    // The bar reads progressMs while drawing; the label only recomposes when the second changes.
    val elapsedSeconds by remember(nowPlaying) { derivedStateOf { progressMs / MILLIS_PER_SECOND } }
    LaunchedEffect(nowPlaying) {
        if (!nowPlaying.isPlaying) return@LaunchedEffect
        // Infinite-animation frames honour InfiniteAnimationPolicy (paused in UI tests).
        val start = withInfiniteAnimationFrameMillis { it }
        while (progressMs < track.durationMs) {
            withInfiniteAnimationFrameMillis { frame ->
                progressMs = (nowPlaying.progressMs + frame - start).coerceAtMost(track.durationMs)
            }
        }
    }
    val breathing = rememberInfiniteTransition(label = "breathe")
    val reduce = LocalReduceMotion.current
    val scale by breathing.animateFloat(
        initialValue = 1f,
        targetValue = if (reduce || !nowPlaying.isPlaying) 1f else BREATHE_SCALE,
        animationSpec = infiniteRepeatable(tween(BREATHE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.55f) } + MaterialTheme.colorScheme.surfaceContainer))
            .bouncyClickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = track,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
                label = "artwork",
            ) { current ->
                Artwork(
                    imageUrl = current.album.imageUrl,
                    title = current.name,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EqualizerBars(color = Color.White, playing = nowPlaying.isPlaying)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (nowPlaying.isPlaying) R.string.feature_home_now_playing else R.string.feature_home_paused),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    track.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artistNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (track.durationMs > 0) progressMs.toFloat() / track.durationMs else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        Formatters.trackDuration(elapsedSeconds * MILLIS_PER_SECOND),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        Formatters.trackDuration(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private const val BREATHE_SCALE = 1.05f
private const val BREATHE_MS = 1_600
private const val MILLIS_PER_SECOND = 1_000L
