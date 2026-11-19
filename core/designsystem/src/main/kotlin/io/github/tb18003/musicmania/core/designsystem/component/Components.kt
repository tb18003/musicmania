/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.Motion
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.designsystem.theme.StatNumberStyle
import io.github.tb18003.musicmania.core.designsystem.theme.motionSpring
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToLong

/** Counts up from 0 (or the previous value) to [value] with a decelerating curve. */
@Composable
fun AnimatedCounter(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = StatNumberStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    suffix: String = "",
    durationMillis: Int = Motion.EXTRA_LONG,
) {
    val reduceMotion = LocalReduceMotion.current
    val animated = remember { Animatable(if (reduceMotion) value.toFloat() else 0f) }
    LaunchedEffect(value, reduceMotion) {
        if (reduceMotion) {
            animated.snapTo(
                value.toFloat(),
            )
        } else {
            animated.animateTo(value.toFloat(), tween(durationMillis, easing = Motion.EmphasizedDecelerate))
        }
    }
    val format = remember { NumberFormat.getIntegerInstance() }
    val formatted = format.format(animated.value.roundToLong()) + suffix
    Text(
        text = formatted,
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = format.format(value) + suffix },
    )
}

/** Pill-shaped segmented control whose indicator springs between options. */
@Composable
fun SegmentedSelector(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(segment * selectedIndex, motionSpring(), label = "indicator")
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segment)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .semantics {
                            this.selected = selected
                            role = Role.Tab
                        }
                        .bouncyClickable(role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Large stat card: label + animated number + optional delta vs previous period. */
@Composable
fun StatTile(label: String, value: Long, modifier: Modifier = Modifier, suffix: String = "", delta: Double? = null) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedCounter(
                value,
                suffix = suffix,
                style = StatNumberStyle.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize),
            )
            if (delta != null) DeltaChip(delta)
        }
    }
}

/** "▲ 12%" / "▼ 4%" coloured chip. */
@Composable
fun DeltaChip(delta: Double, modifier: Modifier = Modifier) {
    val positive = delta >= 0
    val color = if (positive) MusicManiaTheme.extendedColors.positive else MusicManiaTheme.extendedColors.negative
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (positive) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("${(abs(delta) * PERCENT).roundToLong()}%", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Little animated equaliser shown next to the track that is playing right now. */
@Composable
fun EqualizerBars(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, playing: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "eq")
    val reduce = LocalReduceMotion.current || !playing
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(420, 560, 360).forEachIndexed { i, period ->
            val fraction by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = if (reduce) 0.25f else 1f,
                animationSpec = infiniteRepeatable(tween(period, delayMillis = i * 90, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** Primary call-to-action with the brand gradient and a bouncy press. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    height: Dp = 56.dp,
) {
    val colors = MusicManiaTheme.extendedColors.brandGradient
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(if (enabled) colors else colors.map { it.copy(alpha = 0.4f) }))
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFF0A0A0F), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val PERCENT = 100
