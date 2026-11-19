/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

import java.time.Instant

/** Periods a ranking/statistic can be computed for. */
enum class TimeRange(val days: Long?) {
    FOUR_WEEKS(28),
    SIX_MONTHS(182),
    ONE_YEAR(365),

    /** Whole local history (synced plays + imported Extended Streaming History). */
    LIFETIME(null),
    ;

    /** True when Spotify's `/me/top` endpoint can answer for this range. */
    val isApiBacked: Boolean get() = this != LIFETIME
}

/** How an item moved compared to the previous ranking snapshot. */
sealed interface RankMovement {
    data object New : RankMovement

    data object Same : RankMovement

    data class Up(val places: Int) : RankMovement

    data class Down(val places: Int) : RankMovement

    /** No previous snapshot to compare against. */
    data object Unknown : RankMovement
}

data class Ranked<T>(
    val rank: Int,
    val item: T,
    val movement: RankMovement = RankMovement.Unknown,
    val streams: Int? = null,
    val listenedMs: Long? = null,
)

/** A ranked list as returned by the API plus the previous snapshot used for movement arrows. */
data class TopList<T>(
    val items: List<T>,
    val previousRanks: Map<String, Int>?,
    val fetchedAt: Instant?,
)

data class Genre(
    val name: String,
    /** Normalised 0..1 relative weight. */
    val score: Double,
    val artistCount: Int,
)

data class TopAlbum(
    val album: AlbumSummary,
    val artistName: String,
    val score: Double,
)
