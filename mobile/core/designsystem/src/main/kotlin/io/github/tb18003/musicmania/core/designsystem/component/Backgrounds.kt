/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.Motion
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slowly drifting "mesh" of soft radial blobs. Colours cross-fade when [colors] change
 * (e.g. to the palette of the album art currently playing).
 */
@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = MusicManiaTheme.extendedColors.brandGradient,
    intensity: Float = 0.35f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    val c1 by animateColorAsState(colors.getOrElse(0) { Color.Transparent }, tween(Motion.EXTRA_LONG), label = "blob1")
    val c2 by animateColorAsState(colors.getOrElse(1) { c1 }, tween(Motion.EXTRA_LONG), label = "blob2")
    val c3 by animateColorAsState(colors.getOrElse(2) { c2 }, tween(Motion.EXTRA_LONG), label = "blob3")
    val transition = rememberInfiniteTransition(label = "mesh")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(DRIFT_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(background)
            blob(c1.copy(alpha = intensity), 0.2f + 0.08f * cos(phase), 0.15f + 0.06f * sin(phase), 0.75f)
            blob(c2.copy(alpha = intensity * 0.8f), 0.85f + 0.07f * sin(phase * 1.3f), 0.35f + 0.08f * cos(phase), 0.65f)
            blob(c3.copy(alpha = intensity * 0.7f), 0.45f + 0.1f * cos(phase * 0.7f), 0.8f + 0.05f * sin(phase), 0.8f)
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
    }
}

private fun DrawScope.blob(color: Color, fx: Float, fy: Float, radiusFraction: Float) {
    val center = Offset(size.width * fx, size.height * fy)
    val radius = size.maxDimension * radiusFraction
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius), radius, center)
}

private const val DRIFT_PERIOD_MS = 18_000
