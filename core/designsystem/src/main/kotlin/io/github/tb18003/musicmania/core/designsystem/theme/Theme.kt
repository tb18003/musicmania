/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Palette.Volt,
    onPrimary = Palette.Ink,
    primaryContainer = Palette.Ink4,
    onPrimaryContainer = Palette.Volt,
    secondary = Palette.Violet,
    onSecondary = Palette.Ink,
    tertiary = Palette.Coral,
    onTertiary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Palette.Snow,
    surface = Palette.Ink,
    onSurface = Palette.Snow,
    surfaceVariant = Palette.Ink3,
    onSurfaceVariant = Palette.Mist,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Ink2,
    surfaceContainer = Palette.Ink3,
    surfaceContainerHigh = Palette.Ink4,
    surfaceContainerHighest = Palette.Ink5,
    outline = Palette.Ink5,
    outlineVariant = Palette.Ink4,
    error = Palette.Rose,
    onError = Palette.Ink,
)

private val LightScheme = lightColorScheme(
    primary = Palette.VioletDeep,
    onPrimary = Palette.White,
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Palette.VioletDeep,
    secondary = Palette.VoltDeep,
    onSecondary = Palette.White,
    tertiary = Palette.CoralDeep,
    onTertiary = Palette.White,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEDEDF3),
    onSurfaceVariant = Color(0xFF5B5B70),
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Color(0xFFF1F1F6),
    surfaceContainer = Color(0xFFECECF2),
    surfaceContainerHigh = Color(0xFFE5E5EE),
    surfaceContainerHighest = Color(0xFFDEDEE9),
    outline = Color(0xFFCFCFDC),
    outlineVariant = Color(0xFFE2E2EA),
    error = Palette.CoralDeep,
)

private val MusicManiaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * App theme. Dark by default (music apps live in the dark). Dynamic colour (Material You)
 * is opt-in because the brand palette is part of the identity.
 */
@Composable
fun MusicManiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val systemReduceMotion = remember(context) {
        // Honour the system "Remove animations" accessibility setting.
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
        LocalReduceMotion provides (reduceMotion || systemReduceMotion),
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = MusicManiaTypography, shapes = MusicManiaShapes, content = content)
    }
}

/** Shorthand: `MusicManiaTheme.extendedColors.positive`. */
object MusicManiaTheme {
    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
