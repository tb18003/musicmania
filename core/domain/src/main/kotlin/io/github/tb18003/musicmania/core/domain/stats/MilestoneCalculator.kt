/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.stats

import io.github.tb18003.musicmania.core.model.ListeningSummary
import io.github.tb18003.musicmania.core.model.Milestone
import io.github.tb18003.musicmania.core.model.MilestoneType

/** Achievement badges unlocked by lifetime totals. */
object MilestoneCalculator {
    val thresholds: Map<MilestoneType, List<Long>> = mapOf(
        MilestoneType.MINUTES to listOf(1_000L, 10_000L, 50_000L, 100_000L, 250_000L),
        MilestoneType.STREAMS to listOf(1_000L, 5_000L, 25_000L, 100_000L),
        MilestoneType.ARTISTS to listOf(50L, 250L, 1_000L, 2_500L),
        MilestoneType.STREAK_DAYS to listOf(7L, 30L, 100L, 365L),
    )

    fun compute(lifetime: ListeningSummary, longestStreak: Int): List<Milestone> {
        val values = mapOf(
            MilestoneType.MINUTES to lifetime.minutes,
            MilestoneType.STREAMS to lifetime.streams.toLong(),
            MilestoneType.ARTISTS to lifetime.uniqueArtists.toLong(),
            MilestoneType.STREAK_DAYS to longestStreak.toLong(),
        )
        return thresholds.flatMap { (type, levels) ->
            levels.map { threshold -> Milestone(type, threshold, values.getValue(type)) }
        }
    }

    /** The closest not-yet-achieved milestone per type, useful for "next goal" UI. */
    fun nextGoals(milestones: List<Milestone>): List<Milestone> =
        milestones.groupBy { it.type }.mapNotNull { (_, list) -> list.firstOrNull { !it.achieved } }
}
