/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.tb18003.musicmania.core.designsystem.theme.LocalEntranceState
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.Motion
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Lightweight Canvas charts. No chart library: full control over motion and styling,
 * zero extra dependencies. Every chart animates in once and exposes a text summary
 * for TalkBack through `contentDescription`.
 */

/**
 * 0 -> 1 progress that runs once when [key] changes. Instant with reduced motion, and when a
 * revisited screen is just reloading its data (see [LocalEntranceState]).
 */
@Composable
fun rememberEntranceProgress(key: Any?, durationMillis: Int = Motion.LONG, delayMillis: Int = 0): Float {
    val reduce = LocalReduceMotion.current
    val entrance = LocalEntranceState.current
    val progress = remember(key) { Animatable(if (reduce || !entrance.shouldAnimateDataChange()) 1f else 0f) }
    LaunchedEffect(key, reduce) {
        if (reduce || !entrance.shouldAnimateDataChange()) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, tween(durationMillis, delayMillis, Motion.EmphasizedDecelerate))
        }
    }
    return progress.value
}

/** Vertical bars growing from the baseline, left to right. */
@Composable
fun BarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    barColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    description: String = "",
) {
    val progress = rememberEntranceProgress(values)
    Canvas(modifier.semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().takeIf { it > 0f } ?: 1f
        val slot = size.width / values.size
        val barWidth = slot * BAR_FILL
        values.forEachIndexed { index, value ->
            // Stagger: each bar starts a little after the previous one.
            val local = ((progress * (values.size + STAGGER_BARS) - index) / STAGGER_BARS).coerceIn(0f, 1f)
            val height = size.height * (value / max) * local
            drawRoundRect(
                color = if (index == highlightIndex) highlightColor else barColor,
                topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** Smooth area chart revealed left to right. */
@Composable
fun AreaChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    description: String = "",
) {
    val progress = rememberEntranceProgress(values, durationMillis = Motion.EXTRA_LONG)
    Canvas(modifier.semantics { contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val max = values.max().takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v -> Offset(i * stepX, size.height - size.height * (v / max) * CHART_HEADROOM) }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (prev.x + cur.x) / 2
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        clipRect(right = size.width * progress) {
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * 24-hour "listening clock": one radial bar per hour, midnight at the top.
 * The peak hour is highlighted.
 */
@Composable
fun RadialClockChart(msByHour: List<Long>, modifier: Modifier = Modifier, description: String = "") {
    val progress = rememberEntranceProgress(msByHour, durationMillis = Motion.EXTRA_LONG)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val colors = MusicManiaTheme.extendedColors.brandGradient
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val max = msByHour.maxOrNull()?.takeIf { it > 0 } ?: 1L
        val peak = msByHour.indexOf(max)
        val radius = min(size.width, size.height) / 2
        val inner = radius * INNER_RADIUS
        val barWidth = (2 * PI * inner / HOURS * BAR_FILL).toFloat()
        msByHour.forEachIndexed { hour, ms ->
            val fraction = ms.toFloat() / max
            val length = (radius * OUTER_RADIUS - inner) * fraction * progress
            rotate(degrees = hour * (360f / HOURS)) {
                drawLine(
                    track,
                    Offset(center.x, center.y - inner),
                    Offset(center.x, center.y - radius * OUTER_RADIUS),
                    barWidth,
                    StrokeCap.Round,
                )
                if (length > 0f) {
                    drawLine(
                        brush = Brush.verticalGradient(
                            listOf(colors.last(), if (hour == peak) colors.first() else colors[1]),
                            startY = center.y - inner,
                            endY = center.y - inner - length,
                        ),
                        start = Offset(center.x, center.y - inner),
                        end = Offset(center.x, center.y - inner - length),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        listOf(0, 6, 12, 18).forEach { hour -> drawHourLabel(measurer, hour, inner * LABEL_RADIUS, labelStyle) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHourLabel(
    measurer: TextMeasurer,
    hour: Int,
    radius: Float,
    style: androidx.compose.ui.text.TextStyle,
) {
    val angle = hour / HOURS.toFloat() * 2 * PI - PI / 2
    val layout = measurer.measure(hour.toString().padStart(2, '0'), style)
    val x = center.x + (radius * cos(angle)).toFloat() - layout.size.width / 2
    val y = center.y + (radius * sin(angle)).toFloat() - layout.size.height / 2
    drawText(layout, topLeft = Offset(x, y))
}

/** 7 x 24 grid (weekday x hour) with colour intensity proportional to listening time. */
@Composable
fun HeatmapChart(cells: List<List<Long>>, modifier: Modifier = Modifier, rowLabels: List<String> = emptyList(), description: String = "") {
    val progress = rememberEntranceProgress(cells)
    val low = MusicManiaTheme.extendedColors.heatmapLow
    val high = MusicManiaTheme.extendedColors.heatmapHigh
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(modifier.semantics { contentDescription = description }) {
        val rows = cells.size.takeIf { it > 0 } ?: return@Canvas
        val cols = cells.maxOf { it.size }.takeIf { it > 0 } ?: return@Canvas
        val max = cells.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1L
        val gap = 2.dp.toPx()
        val labelWidth = if (rowLabels.isEmpty()) 0f else HEATMAP_LABEL_WIDTH.dp.toPx()
        val cell = min((size.width - labelWidth - gap * (cols - 1)) / cols, (size.height - gap * (rows - 1)) / rows)
        cells.forEachIndexed { r, row ->
            rowLabels.getOrNull(r)?.let { label ->
                val layout = measurer.measure(label, labelStyle)
                drawText(layout, topLeft = Offset(0f, r * (cell + gap) + (cell - layout.size.height) / 2))
            }
            row.forEachIndexed { c, ms ->
                // Diagonal wave reveal
                val local = (progress * 2f - (r + c) / (rows + cols).toFloat()).coerceIn(0f, 1f)
                val intensity = (ms.toFloat() / max) * local
                drawRoundRect(
                    color = lerp(low, high, intensity),
                    topLeft = Offset(labelWidth + c * (cell + gap), r * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell / 4),
                )
            }
        }
    }
}

/** Animated progress ring (milestones, import progress). [progress] in 0..1. */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidthDp: Float = 10f,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val entrance = rememberEntranceProgress(progress)
    val colors = MusicManiaTheme.extendedColors.brandGradient
    Canvas(modifier.aspectRatio(1f)) {
        val stroke = strokeWidthDp.dp.toPx()
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(
            brush = Brush.sweepGradient(colors + colors.first()),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f) * entrance,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Donut of shares (e.g. listening per platform). */
@Composable
fun DonutChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    colors: List<Color> = MusicManiaTheme.extendedColors.chart,
    description: String = "",
) {
    val progress = rememberEntranceProgress(values)
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val total = values.sum().takeIf { it > 0f } ?: return@Canvas
        val stroke = size.minDimension * DONUT_STROKE
        val arcSize = Size(size.width - stroke, size.height - stroke)
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = 360f * (v / total) * progress
            drawArc(
                colors[i % colors.size],
                start,
                (sweep - DONUT_GAP_DEG).coerceAtLeast(0f),
                false,
                Offset(stroke / 2, stroke / 2),
                arcSize,
                style = Stroke(stroke),
            )
            start += sweep
        }
    }
}

private const val BAR_FILL = 0.62f
private const val STAGGER_BARS = 6f
private const val CHART_HEADROOM = 0.9f
private const val HOURS = 24
private const val INNER_RADIUS = 0.34f
private const val OUTER_RADIUS = 0.98f
private const val LABEL_RADIUS = 0.62f
private const val DONUT_STROKE = 0.16f
private const val DONUT_GAP_DEG = 2f
private const val HEATMAP_LABEL_WIDTH = 16
