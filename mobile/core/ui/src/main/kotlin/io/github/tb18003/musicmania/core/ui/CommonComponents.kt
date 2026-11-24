/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.SegmentedSelector
import io.github.tb18003.musicmania.core.designsystem.component.bouncyClickable
import io.github.tb18003.musicmania.core.designsystem.component.shimmer
import io.github.tb18003.musicmania.core.designsystem.theme.DisplayFontFamily
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.RankMovement
import io.github.tb18003.musicmania.core.model.TimeRange

@Composable
fun TimeRangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
    ranges: List<TimeRange> = TimeRange.entries,
) {
    SegmentedSelector(
        options = ranges.map { stringResource(it.labelRes()) },
        selectedIndex = ranges.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(ranges[it]) },
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** ▲3 / ▼2 / NEW indicator, animated when the value changes. */
@Composable
fun MovementIndicator(movement: RankMovement, modifier: Modifier = Modifier) {
    val positive = MusicManiaTheme.extendedColors.positive
    val negative = MusicManiaTheme.extendedColors.negative
    val description = when (movement) {
        is RankMovement.Up -> stringResource(R.string.core_ui_movement_up, movement.places)
        is RankMovement.Down -> stringResource(R.string.core_ui_movement_down, movement.places)
        RankMovement.New -> stringResource(R.string.core_ui_movement_new)
        else -> ""
    }
    AnimatedContent(
        targetState = movement,
        transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() },
        modifier = modifier.semantics { contentDescription = description },
        label = "movement",
    ) { target ->
        when (target) {
            is RankMovement.Up -> MovementLabel(Icons.Rounded.ArrowDropUp, target.places.toString(), positive)
            is RankMovement.Down -> MovementLabel(Icons.Rounded.ArrowDropDown, target.places.toString(), negative)
            RankMovement.New -> Text(
                stringResource(R.string.core_ui_movement_new_short),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            RankMovement.Same, RankMovement.Unknown -> Spacer(Modifier.size(1.dp))
        }
    }
}

@Composable
private fun MovementLabel(icon: ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Row used by every ranked or chronological list. [sharedKey] enables the artwork
 * to fly into the detail screen.
 */
@Composable
fun MediaRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    movement: RankMovement = RankMovement.Unknown,
    trailing: String? = null,
    circular: Boolean = false,
    sharedKey: String? = null,
    artworkSize: Dp = 52.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncyClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Column(Modifier.widthIn(min = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    rank.toString(),
                    fontFamily = DisplayFontFamily,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MovementIndicator(movement)
            }
            Spacer(Modifier.width(8.dp))
        }
        Artwork(
            imageUrl = imageUrl,
            title = title,
            shape = if (circular) CircleShape else MaterialTheme.shapes.small,
            modifier = Modifier
                .size(artworkSize)
                .then(if (sharedKey != null) Modifier.sharedElementOrSelf(sharedKey) else Modifier),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MediaRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).shimmer(MaterialTheme.shapes.small))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(14.dp).width(160.dp).shimmer())
            Box(Modifier.height(12.dp).width(100.dp).shimmer())
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.GraphicEq,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(error: AppError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        title = stringResource(R.string.core_ui_error_title),
        message = error.message(),
        icon = Icons.Rounded.CloudOff,
        actionLabel = stringResource(R.string.core_ui_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}
