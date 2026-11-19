/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.stats

import io.github.tb18003.musicmania.core.model.Streaks
import java.time.LocalDate

/** Consecutive-days-listening streaks. */
object StreakCalculator {
    /**
     * @param activeDays days with at least one play (any order, duplicates allowed).
     * @param today the current local date; a streak is still "current" if the last
     * active day is today or yesterday (the user may not have listened yet today).
     */
    fun compute(activeDays: Collection<LocalDate>, today: LocalDate): Streaks {
        val days = activeDays.toSortedSet()
        if (days.isEmpty()) return Streaks(current = 0, longest = 0, longestEndedOn = null)

        var longest = 0
        var longestEnd: LocalDate? = null
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            if (run >= longest) {
                longest = run
                longestEnd = day
            }
            previous = day
        }

        val last = days.last()
        val current = if (last == today || last == today.minusDays(1)) run else 0
        return Streaks(current = current, longest = longest, longestEndedOn = longestEnd)
    }
}
