/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.ui

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.model.ListeningPersonality
import io.github.tb18003.musicmania.core.model.RankMovement
import io.github.tb18003.musicmania.core.model.TimeRange
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Pure, locale-aware formatting helpers (unit tested). */
object Formatters {
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val THOUSAND = 1_000.0
    private const val MILLION = 1_000_000.0

    fun minutes(ms: Long): Long = ms / MS_PER_MINUTE

    fun number(value: Long, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(value)

    /** 1234 -> "1.2K", 2500000 -> "2.5M". */
    fun compact(value: Long, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
        return when {
            value >= MILLION -> format.format(value / MILLION) + "M"
            value >= THOUSAND -> format.format(value / THOUSAND) + "K"
            else -> value.toString()
        }
    }

    /** Track length "3:07". */
    fun trackDuration(ms: Long): String {
        val totalSeconds = ms / MS_PER_SECOND
        return "%d:%02d".format(totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
    }

    fun date(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(zone))

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

    fun relative(instant: Instant, now: Long = System.currentTimeMillis()): String =
        DateUtils.getRelativeTimeSpanString(instant.toEpochMilli(), now, DateUtils.MINUTE_IN_MILLIS).toString()

    /** Short accessible label for a ranking movement, e.g. "+3", "−2", "NEW". */
    fun movement(movement: RankMovement): String? = when (movement) {
        is RankMovement.Up -> "+${movement.places}"
        is RankMovement.Down -> "−${movement.places}"
        RankMovement.New -> "NEW"
        RankMovement.Same, RankMovement.Unknown -> null
    }
}

@StringRes
fun TimeRange.labelRes(): Int = when (this) {
    TimeRange.FOUR_WEEKS -> R.string.core_ui_range_four_weeks
    TimeRange.SIX_MONTHS -> R.string.core_ui_range_six_months
    TimeRange.ONE_YEAR -> R.string.core_ui_range_one_year
    TimeRange.LIFETIME -> R.string.core_ui_range_lifetime
}

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.core_ui_error_network
    AppError.Unauthorized -> R.string.core_ui_error_unauthorized
    AppError.Forbidden -> R.string.core_ui_error_forbidden
    AppError.NotFound -> R.string.core_ui_error_not_found
    is AppError.RateLimited -> R.string.core_ui_error_rate_limited
    is AppError.Server -> R.string.core_ui_error_server
    is AppError.InvalidData -> R.string.core_ui_error_invalid_data
    AppError.Storage -> R.string.core_ui_error_storage
    is AppError.Unknown -> R.string.core_ui_error_unknown
}

@Composable
@ReadOnlyComposable
fun AppError.message(): String = stringResource(messageRes())

@StringRes
fun ListeningPersonality.titleRes(): Int = when (this) {
    ListeningPersonality.EARLY_BIRD -> R.string.core_ui_personality_early_bird
    ListeningPersonality.DAYTIME_CRUISER -> R.string.core_ui_personality_daytime
    ListeningPersonality.EVENING_DRIFTER -> R.string.core_ui_personality_evening
    ListeningPersonality.NIGHT_OWL -> R.string.core_ui_personality_night_owl
}
