/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.tb18003.musicmania.core.database.model.AlbumEntity
import io.github.tb18003.musicmania.core.database.model.ArtistEntity
import io.github.tb18003.musicmania.core.database.model.RankedArtistRow
import io.github.tb18003.musicmania.core.database.model.RankedTrackRow
import io.github.tb18003.musicmania.core.database.model.SnapshotRankRow
import io.github.tb18003.musicmania.core.database.model.TopSnapshotEntity
import io.github.tb18003.musicmania.core.database.model.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrack(id: String): TrackEntity?

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtist(id: String): ArtistEntity?

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbum(id: String): AlbumEntity?
}

@Dao
interface TopSnapshotDao {
    @Query("DELETE FROM top_snapshots WHERE type = :type AND timeRange = :timeRange AND snapshotDay = :day")
    suspend fun deleteSnapshot(type: String, timeRange: String, day: Long)

    @Insert
    suspend fun insertAll(rows: List<TopSnapshotEntity>)

    /** Replaces today's snapshot atomically (a refresh later in the day overwrites it). */
    @Transaction
    suspend fun replaceSnapshot(type: String, timeRange: String, day: Long, rows: List<TopSnapshotEntity>) {
        deleteSnapshot(type, timeRange, day)
        insertAll(rows)
    }

    @Query("DELETE FROM top_snapshots WHERE snapshotDay < :beforeDay")
    suspend fun pruneOlderThan(beforeDay: Long)

    @Query(
        """
        SELECT t.*, s.position AS position, s.fetchedAt AS fetchedAt
        FROM top_snapshots s JOIN tracks t ON t.id = s.itemId
        WHERE s.type = 'track' AND s.timeRange = :timeRange AND s.snapshotDay =
            (SELECT MAX(snapshotDay) FROM top_snapshots WHERE type = 'track' AND timeRange = :timeRange)
        ORDER BY s.position
        """,
    )
    fun observeLatestTracks(timeRange: String): Flow<List<RankedTrackRow>>

    @Query(
        """
        SELECT a.*, s.position AS position, s.fetchedAt AS fetchedAt
        FROM top_snapshots s JOIN artists a ON a.id = s.itemId
        WHERE s.type = 'artist' AND s.timeRange = :timeRange AND s.snapshotDay =
            (SELECT MAX(snapshotDay) FROM top_snapshots WHERE type = 'artist' AND timeRange = :timeRange)
        ORDER BY s.position
        """,
    )
    fun observeLatestArtists(timeRange: String): Flow<List<RankedArtistRow>>

    /** Ranks of the most recent snapshot strictly older than the latest one. */
    @Query(
        """
        SELECT itemId, position FROM top_snapshots
        WHERE type = :type AND timeRange = :timeRange AND snapshotDay =
            (SELECT MAX(snapshotDay) FROM top_snapshots WHERE type = :type AND timeRange = :timeRange AND snapshotDay <
                (SELECT MAX(snapshotDay) FROM top_snapshots WHERE type = :type AND timeRange = :timeRange))
        """,
    )
    fun observePreviousRanks(type: String, timeRange: String): Flow<List<SnapshotRankRow>>
}
