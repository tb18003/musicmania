/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tb18003.musicmania.core.designsystem.chart.BarChart
import io.github.tb18003.musicmania.core.designsystem.chart.HeatmapChart
import io.github.tb18003.musicmania.core.designsystem.chart.RadialClockChart
import io.github.tb18003.musicmania.core.designsystem.chart.RingProgress
import io.github.tb18003.musicmania.core.designsystem.component.DeltaChip
import io.github.tb18003.musicmania.core.designsystem.component.GeneratedArtwork
import io.github.tb18003.musicmania.core.designsystem.component.GradientButton
import io.github.tb18003.musicmania.core.designsystem.component.SegmentedSelector
import io.github.tb18003.musicmania.core.designsystem.component.StatTile
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests of the design system in dark and light themes.
 * Record baselines with `./gradlew recordRoborazziDebug`, verify with `verifyRoborazziDebug`.
 * Reduced motion makes every animation render its final frame deterministically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DesignSystemScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun galleryDark() = capture(dark = true, name = "gallery_dark")

    @Test
    fun galleryLight() = capture(dark = false, name = "gallery_light")

    private fun capture(dark: Boolean, name: String) {
        composeRule.setContent {
            MusicManiaTheme(darkTheme = dark, reduceMotion = true) { Gallery() }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Gallery() {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("MusicMania", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            SegmentedSelector(listOf("4 weeks", "6 months", "1 year", "All time"), selectedIndex = 1, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Minutes", 12_840, Modifier.weight(1f), delta = 0.12)
                StatTile("Streams", 3_120, Modifier.weight(1f), delta = -0.04)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GeneratedArtwork("Neon Harbor", Modifier.size(72.dp))
                GeneratedArtwork("Velvet Static", Modifier.size(72.dp))
                RingProgress(0.68f, Modifier.size(72.dp))
                DeltaChip(0.25)
            }
            BarChart(List(12) { ((it * 37) % 11 + 2).toFloat() }, Modifier.fillMaxWidth().height(80.dp), highlightIndex = 7)
            HeatmapChart(List(7) { r -> List(24) { c -> ((r * 7 + c * 13) % 10).toLong() } }, Modifier.fillMaxWidth().height(90.dp))
            RadialClockChart(List(24) { h -> if (h in 18..23) 900L else (h * 37 % 300).toLong() }, Modifier.size(220.dp))
            GradientButton("Connect with Spotify", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
