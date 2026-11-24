/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.repository

import androidx.room.withTransaction
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.data.mapper.toEntity
import io.github.tb18003.musicmania.core.data.mapper.toModel
import io.github.tb18003.musicmania.core.data.mapper.toSpotify
import io.github.tb18003.musicmania.core.database.MusicManiaDatabase
import io.github.tb18003.musicmania.core.database.dao.CatalogDao
import io.github.tb18003.musicmania.core.database.dao.TopSnapshotDao
import io.github.tb18003.musicmania.core.database.model.SnapshotRankRow
import io.github.tb18003.musicmania.core.database.model.TopSnapshotEntity
import io.github.tb18003.musicmania.core.datastore.prefs.AppStateDataSource
import io.github.tb18003.musicmania.core.domain.repository.TopItemsRepository
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopList
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.network.SpotifyNetworkDataSource
import io.github.tb18003.musicmania.core.network.SpotifyTimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Offline-first: the UI always reads Room; [refresh] writes a new daily snapshot.
 * The previous snapshot powers the ▲▼ movement indicators.
 */
internal class OfflineFirstTopItemsRepository @Inject constructor(
    private val network: SpotifyNetworkDataSource,
    private val db: MusicManiaDatabase,
    private val catalogDao: CatalogDao,
    private val snapshotDao: TopSnapshotDao,
    private val appState: AppStateDataSource,
    private val clock: Clock,
) : TopItemsRepository {
    override fun observeTopTracks(range: TimeRange): Flow<TopList<Track>> {
        val apiRange = range.toSpotify().apiValue
        return combine(
            snapshotDao.observeLatestTracks(apiRange),
            snapshotDao.observePreviousRanks(TopSnapshotEntity.TYPE_TRACK, apiRange),
        ) { rows, previous ->
            TopList(rows.map { it.track.toModel() }, previous.toRankMap(), rows.firstOrNull()?.fetchedAt?.let(Instant::ofEpochMilli))
        }
    }

    override fun observeTopArtists(range: TimeRange): Flow<TopList<Artist>> {
        val apiRange = range.toSpotify().apiValue
        return combine(
            snapshotDao.observeLatestArtists(apiRange),
            snapshotDao.observePreviousRanks(TopSnapshotEntity.TYPE_ARTIST, apiRange),
        ) { rows, previous ->
            TopList(rows.map { it.artist.toModel() }, previous.toRankMap(), rows.firstOrNull()?.fetchedAt?.let(Instant::ofEpochMilli))
        }
    }

    override suspend fun refresh(range: TimeRange): DataResult<Unit> {
        if (appState.demoMode.first()) return DataResult.Success(Unit)
        val spotifyRange = range.toSpotify()
        val tracks = when (val result = network.getTopTracks(spotifyRange)) {
            is DataResult.Failure -> return result
            is DataResult.Success -> result.data.mapNotNull { it.toEntity() }
        }
        val artists = when (val result = network.getTopArtists(spotifyRange)) {
            is DataResult.Failure -> return result
            is DataResult.Success -> result.data.map { it.toEntity() }
        }
        val today = LocalDate.now(clock).toEpochDay()
        val now = clock.millis()
        db.withTransaction {
            catalogDao.upsertTracks(tracks)
            catalogDao.upsertArtists(artists)
            snapshotDao.replaceSnapshot(
                TopSnapshotEntity.TYPE_TRACK,
                spotifyRange.apiValue,
                today,
                tracks.mapIndexed { i, t -> snapshotRow(TopSnapshotEntity.TYPE_TRACK, spotifyRange, today, i, t.id, now) },
            )
            snapshotDao.replaceSnapshot(
                TopSnapshotEntity.TYPE_ARTIST,
                spotifyRange.apiValue,
                today,
                artists.mapIndexed { i, a -> snapshotRow(TopSnapshotEntity.TYPE_ARTIST, spotifyRange, today, i, a.id, now) },
            )
            snapshotDao.pruneOlderThan(today - SNAPSHOT_RETENTION_DAYS)
        }
        return DataResult.Success(Unit)
    }

    private fun snapshotRow(type: String, range: SpotifyTimeRange, day: Long, index: Int, id: String, now: Long) =
        TopSnapshotEntity(type = type, timeRange = range.apiValue, snapshotDay = day, position = index + 1, itemId = id, fetchedAt = now)

    private fun List<SnapshotRankRow>.toRankMap(): Map<String, Int>? = takeIf { it.isNotEmpty() }?.associate { it.itemId to it.position }

    private companion object {
        const val SNAPSHOT_RETENTION_DAYS = 400L
    }
}
