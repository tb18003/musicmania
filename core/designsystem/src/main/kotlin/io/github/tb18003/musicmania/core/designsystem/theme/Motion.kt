/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens. Every animation in the app goes through these helpers so the
 * "reduce motion" accessibility preference can disable them in one place.
 */
object Motion {
    const val SHORT = 180
    const val MEDIUM = 320
    const val LONG = 650
    const val EXTRA_LONG = 1_200
    const val STAGGER_MS = 45

    /** Material 3 "emphasized decelerate". */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** True when the user asked for reduced motion (system setting or in-app toggle). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun <T> motionTween(durationMillis: Int = Motion.MEDIUM, delayMillis: Int = 0): AnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else tween(durationMillis, delayMillis, Motion.EmphasizedDecelerate)

@Composable
@ReadOnlyComposable
fun <T> motionSpring(
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessMediumLow,
): AnimationSpec<T> = if (LocalReduceMotion.current) snap() else spring(dampingRatio, stiffness)
