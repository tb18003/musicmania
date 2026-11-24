/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw palette. Screens use semantic roles (MaterialTheme / [ExtendedColors]), never these. */
internal object Palette {
    val Ink = Color(0xFF0A0A0F)
    val Ink2 = Color(0xFF111118)
    val Ink3 = Color(0xFF181822)
    val Ink4 = Color(0xFF22222E)
    val Ink5 = Color(0xFF2E2E3D)
    val Mist = Color(0xFFA3A3B8)
    val Snow = Color(0xFFF4F4F8)
    val Paper = Color(0xFFF7F7FA)
    val White = Color(0xFFFFFFFF)

    val Volt = Color(0xFFC8FF4D)
    val VoltDeep = Color(0xFF4C7A00)
    val Aqua = Color(0xFF4DE8FF)
    val Violet = Color(0xFF8B7CFF)
    val VioletDeep = Color(0xFF5B4BFF)
    val Coral = Color(0xFFFF7A6B)
    val CoralDeep = Color(0xFFD9483A)
    val Rose = Color(0xFFFF5C7A)
    val Mint = Color(0xFF4ADE80)
    val MintDeep = Color(0xFF15803D)
}

/** Brand colours that Material's scheme has no slot for. */
@Immutable
data class ExtendedColors(
    val brandGradient: List<Color>,
    val positive: Color,
    val negative: Color,
    val chart: List<Color>,
    val heatmapLow: Color,
    val heatmapHigh: Color,
)

internal val DarkExtendedColors = ExtendedColors(
    brandGradient = listOf(Palette.Volt, Palette.Aqua, Palette.Violet),
    positive = Palette.Mint,
    negative = Palette.Rose,
    chart = listOf(Palette.Volt, Palette.Violet, Palette.Aqua, Palette.Coral, Palette.Mint, Palette.Rose),
    heatmapLow = Palette.Ink4,
    heatmapHigh = Palette.Volt,
)

internal val LightExtendedColors = ExtendedColors(
    brandGradient = listOf(Palette.VioletDeep, Palette.Violet, Palette.Coral),
    positive = Palette.MintDeep,
    negative = Palette.CoralDeep,
    chart = listOf(Palette.VioletDeep, Palette.VoltDeep, Palette.CoralDeep, Palette.Violet, Palette.MintDeep, Palette.Rose),
    heatmapLow = Color(0xFFE9E9F2),
    heatmapHigh = Palette.VioletDeep,
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
