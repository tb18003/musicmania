/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.Motion
import io.github.tb18003.musicmania.core.designsystem.theme.motionSpring

/** Clickable that squishes on press with a spring and gives a light haptic tick. */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, motionSpring(), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interaction, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

/** Skeleton loading shimmer. */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    if (LocalReduceMotion.current) return@composed background(base, shape)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing)),
        label = "shimmerProgress",
    )
    background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(progress * SHIMMER_WIDTH, 0f),
            end = Offset((progress + 1f) * SHIMMER_WIDTH, SHIMMER_WIDTH / 2),
        ),
        shape,
    )
}

/**
 * Fades and slides an item in once, delayed by its [index]: lists "cascade" into place.
 */
fun Modifier.staggeredEntrance(index: Int, maxStaggered: Int = 12): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(Motion.MEDIUM, delayMillis = index.coerceAtMost(maxStaggered) * Motion.STAGGER_MS))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * ENTRANCE_OFFSET_PX
    }
}

private const val PRESSED_SCALE = 0.95f
private const val SHIMMER_MS = 1_300
private const val SHIMMER_WIDTH = 600f
private const val ENTRANCE_OFFSET_PX = 48f
