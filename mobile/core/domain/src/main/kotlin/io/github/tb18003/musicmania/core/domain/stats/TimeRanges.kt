/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.stats

import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.TimeRange
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Converts a relative [TimeRange] into an absolute window ending now. */
fun TimeRange.toDateRange(clock: Clock): DateRange {
    // Small margin so plays recorded "right now" are inside the half-open range.
    val end = clock.instant().plus(Duration.ofMinutes(1))
    val start = days?.let { end.minus(Duration.ofDays(it)) } ?: Instant.EPOCH
    return DateRange(start, end)
}

fun LocalDate.toDateRange(clock: Clock): DateRange {
    val zone = clock.zone
    return DateRange(atStartOfDay(zone).toInstant(), plusDays(1).atStartOfDay(zone).toInstant())
}

fun YearMonth.toDateRange(clock: Clock): DateRange {
    val zone = clock.zone
    return DateRange(atDay(1).atStartOfDay(zone).toInstant(), plusMonths(1).atDay(1).atStartOfDay(zone).toInstant())
}

fun yearToDateRange(year: Int, clock: Clock): DateRange {
    val zone = clock.zone
    return DateRange(
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant(),
        LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant(),
    )
}
