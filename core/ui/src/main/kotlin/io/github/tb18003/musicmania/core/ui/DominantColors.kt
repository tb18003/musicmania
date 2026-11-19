/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.tb18003.musicmania.core.designsystem.component.gradientFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts vibrant colours from artwork to tint backgrounds (Palette API).
 * Falls back to the deterministic generated gradient when there is no image.
 */
@Composable
fun rememberArtworkColors(imageUrl: String?, fallbackSeed: String): List<Color> {
    val context = LocalContext.current
    var colors by remember(imageUrl, fallbackSeed) { mutableStateOf(gradientFor(fallbackSeed)) }
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(imageUrl).size(PALETTE_SIZE).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        colors = withContext(Dispatchers.Default) { extract(result.image.toBitmap()) } ?: colors
    }
    return colors
}

private fun extract(bitmap: Bitmap): List<Color>? {
    val palette = Palette.from(bitmap).maximumColorCount(PALETTE_COLORS).generate()
    val swatches = listOfNotNull(
        palette.vibrantSwatch,
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.mutedSwatch,
        palette.dominantSwatch,
    )
        .map { Color(it.rgb) }
        .distinct()
    return swatches.takeIf { it.size >= 2 }?.take(MAX_COLORS)
}

private const val PALETTE_SIZE = 128
private const val PALETTE_COLORS = 16
private const val MAX_COLORS = 3
