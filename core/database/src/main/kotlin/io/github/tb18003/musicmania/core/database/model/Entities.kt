/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class ArtistRef(
    val id: String?,
    val name: String,
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artists: List<ArtistRef>,
    val albumId: String?,
    val albumName: String,
    val imageUrl: String?,
    val releaseDate: String?,
    val durationMs: Long,
    val explicit: Boolean,
    val uri: String?,
)

@Entity(tableName = "artists", indices = [Index("name")])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String?,
    val genres: List<String>,
    val uri: String?,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artists: List<ArtistRef>,
    val imageUrl: String?,
    val releaseDate: String?,
    val totalTracks: Int?,
    val uri: String?,
)

/**
 * One row per (list, day, rank). Keeping daily snapshots gives offline-first caching
 * (latest snapshot = current list) *and* ranking movement (compare with previous day).
 */
@Entity(
    tableName = "top_snapshots",
    primaryKeys = ["type", "timeRange", "snapshotDay", "position"],
    indices = [Index("type", "timeRange", "snapshotDay")],
)
data class TopSnapshotEntity(
    val type: String,
    val timeRange: String,
    /** `LocalDate.toEpochDay()` of the snapshot. */
    val snapshotDay: Long,
    val position: Int,
    val itemId: String,
    val fetchedAt: Long,
) {
    companion object {
        const val TYPE_TRACK = "track"
        const val TYPE_ARTIST = "artist"
    }
}

/**
 * A single play. Names are denormalised on purpose: imported history (basic format)
 * has no Spotify IDs, so aggregations group by names to unify both sources.
 * [dedupeKey] prevents double counting when sync and import overlap.
 */
@Entity(
    tableName = "play_events",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index("playedAt"),
        Index("artistName"),
        Index("trackName", "artistName"),
    ],
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String?,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val imageUrl: String?,
    /** Epoch millis of the moment the play ended (Spotify semantics). */
    val playedAt: Long,
    val msPlayed: Long,
    val source: String,
    val skipped: Boolean?,
    val shuffle: Boolean?,
    val platform: String?,
    val dedupeKey: String,
) {
    companion object {
        private const val MILLIS_PER_SECOND = 1_000L

        /** Same song by the same artist ending in the same second is the same play. */
        fun dedupeKeyOf(trackName: String, artistName: String, playedAtMs: Long): String =
            "${trackName.trim().lowercase()}|${artistName.trim().lowercase()}|${playedAtMs / MILLIS_PER_SECOND}"
    }
}

data class RankedTrackRow(
    @Embedded val track: TrackEntity,
    val position: Int,
    val fetchedAt: Long,
)

data class RankedArtistRow(
    @Embedded val artist: ArtistEntity,
    val position: Int,
    val fetchedAt: Long,
)

data class SnapshotRankRow(
    val itemId: String,
    val position: Int,
)
