/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

import java.time.Instant

data class NowPlaying(val track: Track, val progressMs: Long, val isPlaying: Boolean, val fetchedAt: Instant)

enum class PlaySource { SYNC, IMPORT, DEMO }

/** A single play of a track, either synced from the API or imported from a data export. */
data class PlayEvent(
    val id: Long = 0,
    val trackId: String?,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val imageUrl: String?,
    val playedAt: Instant,
    val msPlayed: Long,
    val source: PlaySource,
    val skipped: Boolean? = null,
    val shuffle: Boolean? = null,
    val platform: String? = null,
)
