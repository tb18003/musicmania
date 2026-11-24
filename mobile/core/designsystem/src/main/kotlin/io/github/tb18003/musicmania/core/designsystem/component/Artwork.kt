/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.SubcomposeAsyncImage
import io.github.tb18003.musicmania.core.designsystem.theme.DisplayFontFamily
import kotlin.math.abs

/**
 * Album/artist artwork with a generated placeholder: imported history and demo data
 * have no images, so every item still gets a unique, deterministic gradient.
 */
@Composable
fun Artwork(
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    contentDescription: String? = null,
) {
    Box(modifier.clip(shape)) {
        if (imageUrl.isNullOrBlank()) {
            GeneratedArtwork(title, Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().shimmer(shape)) },
                error = { GeneratedArtwork(title, Modifier.fillMaxSize()) },
            )
        }
    }
}

@Composable
fun GeneratedArtwork(title: String, modifier: Modifier = Modifier) {
    val colors = remember(title) { gradientFor(title) }
    val initials = remember(title) {
        title.split(' ', '-', '_')
            .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
            .take(2)
            .joinToString("") { it.uppercase() }
            .ifEmpty { "♪" }
    }
    Box(modifier.background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Text(
            initials,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.graphicsLayer { alpha = 0.95f },
        )
    }
}

/** Deterministic pair of vivid colours from a string hash. */
fun gradientFor(seed: String): List<Color> {
    val hash = abs(seed.hashCode())
    val hue1 = (hash % HUE_MAX).toFloat()
    val hue2 = (hue1 + HUE_SHIFT + (hash / HUE_MAX) % HUE_SHIFT) % HUE_MAX
    return listOf(Color.hsv(hue1, SATURATION, VALUE), Color.hsv(hue2, SATURATION, VALUE * 0.75f))
}

private const val HUE_MAX = 360
private const val HUE_SHIFT = 40
private const val SATURATION = 0.62f
private const val VALUE = 0.85f
