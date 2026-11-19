/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.tb18003.musicmania.core.designsystem.R

/** Space Grotesk (SIL OFL 1.1) variable font for display text; system sans for body copy. */
@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: FontWeight) = Font(
    resId = R.font.core_designsystem_space_grotesk,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DisplayFontFamily = FontFamily(
    spaceGrotesk(FontWeight.Normal),
    spaceGrotesk(FontWeight.Medium),
    spaceGrotesk(FontWeight.SemiBold),
    spaceGrotesk(FontWeight.Bold),
)

private val base = Typography()

internal val MusicManiaTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.Bold, (-0.04).em),
    displayMedium = base.displayMedium.display(FontWeight.Bold, (-0.03).em),
    displaySmall = base.displaySmall.display(FontWeight.Bold, (-0.02).em),
    headlineLarge = base.headlineLarge.display(FontWeight.Bold, (-0.02).em),
    headlineMedium = base.headlineMedium.display(FontWeight.SemiBold, (-0.01).em),
    headlineSmall = base.headlineSmall.display(FontWeight.SemiBold, 0.em),
    titleLarge = base.titleLarge.display(FontWeight.SemiBold, 0.em),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.06.em),
)

/** Huge numbers for animated stats ("12,840 min"). */
val StatNumberStyle = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    letterSpacing = (-0.03).em,
)

private fun TextStyle.display(weight: FontWeight, tracking: TextUnit) =
    copy(fontFamily = DisplayFontFamily, fontWeight = weight, letterSpacing = tracking)
