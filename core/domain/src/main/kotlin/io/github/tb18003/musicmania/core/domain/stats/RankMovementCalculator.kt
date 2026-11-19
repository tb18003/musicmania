/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.stats

import io.github.tb18003.musicmania.core.model.RankMovement

/** Pure function computing ▲▼ NEW indicators against the previous snapshot. */
object RankMovementCalculator {
    /**
     * @param currentKeys item keys ordered by current rank (index 0 = rank 1).
     * @param previousRanks key -> 1-based rank in the previous snapshot, null if none exists.
     */
    fun compute(currentKeys: List<String>, previousRanks: Map<String, Int>?): List<RankMovement> {
        if (previousRanks.isNullOrEmpty()) return List(currentKeys.size) { RankMovement.Unknown }
        return currentKeys.mapIndexed { index, key ->
            val currentRank = index + 1
            when (val previous = previousRanks[key]) {
                null -> RankMovement.New
                currentRank -> RankMovement.Same
                else -> if (previous > currentRank) RankMovement.Up(previous - currentRank) else RankMovement.Down(currentRank - previous)
            }
        }
    }
}
