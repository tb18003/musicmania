/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.data.mapper.toEntity
import io.github.tb18003.musicmania.core.data.mapper.toModel
import io.github.tb18003.musicmania.core.data.mapper.toStats
import io.github.tb18003.musicmania.core.database.dao.CatalogDao
import io.github.tb18003.musicmania.core.database.dao.PlayEventDao
import io.github.tb18003.musicmania.core.database.model.PlayEventEntity
import io.github.tb18003.musicmania.core.datastore.prefs.AppStateDataSource
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.model.DailyListening
import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.ItemStats
import io.github.tb18003.musicmania.core.model.ListeningClock
import io.github.tb18003.musicmania.core.model.ListeningSummary
import io.github.tb18003.musicmania.core.model.PlatformShare
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.PlaySource
import io.github.tb18003.musicmania.core.model.PlaybackHabits
import io.github.tb18003.musicmania.core.model.WeekdayHourCell
import io.github.tb18003.musicmania.core.network.SpotifyNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

@Suppress("TooManyFunctions")
internal class DefaultHistoryRepository @Inject constructor(
    private val dao: PlayEventDao,
    private val catalogDao: CatalogDao,
    private val network: SpotifyNetworkDataSource,
    private val appState: AppStateDataSource,
    private val clock: Clock,
) : HistoryRepository {
    override fun pagedHistory(): Flow<PagingData<PlayEvent>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) { dao.pagingSource() }
            .flow
            .map { page -> page.map { it.toModel() } }

    override fun observeSummary(range: DateRange): Flow<ListeningSummary> = range.query(dao::observeSummary).map {
        ListeningSummary(it.listenedMs, it.streams, it.uniqueTracks, it.uniqueArtists)
    }

    override fun observeTopTracks(range: DateRange, limit: Int) =
        dao.observeTopTracks(range.startMs, range.endMs, limit).map { rows -> rows.map { it.toModel() } }

    override fun observeTopArtists(range: DateRange, limit: Int) =
        dao.observeTopArtists(range.startMs, range.endMs, limit).map { rows -> rows.map { it.toModel() } }

    override fun observeTopAlbums(range: DateRange, limit: Int) =
        dao.observeTopAlbums(range.startMs, range.endMs, limit).map { rows -> rows.map { it.toModel() } }

    override fun observeDaily(range: DateRange): Flow<List<DailyListening>> = range.query(dao::observeDaily).map { rows ->
        rows.mapNotNull { row ->
            try {
                DailyListening(LocalDate.parse(row.day), row.listenedMs, row.streams)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    override fun observeClock(range: DateRange): Flow<ListeningClock> = range.query(dao::observeHourly).map { rows ->
        val buckets = LongArray(ListeningClock.HOURS)
        rows.forEach { if (it.hour in buckets.indices) buckets[it.hour] = it.listenedMs }
        ListeningClock(buckets.toList())
    }

    override fun observeWeekdayHour(range: DateRange): Flow<List<WeekdayHourCell>> = range.query(dao::observeWeekdayHour).map { rows ->
        // SQLite %w: 0 = Sunday ... 6 = Saturday
        rows.map { WeekdayHourCell(DayOfWeek.of(if (it.weekday == 0) SUNDAY else it.weekday), it.hour, it.listenedMs) }
    }

    override fun observeHabits(range: DateRange): Flow<PlaybackHabits> = combine(
        range.query(dao::observeHabits),
        range.query(dao::observePlatforms),
    ) { habits, platforms ->
        PlaybackHabits(
            skipRate = habits.skipKnown.takeIf { it > 0 }?.let { habits.skipped.toDouble() / it },
            shuffleRate = habits.shuffleKnown.takeIf { it > 0 }?.let { habits.shuffled.toDouble() / it },
            platforms = platforms.map { PlatformShare(it.platform, it.listenedMs) },
        )
    }

    override fun observeNewArtists(range: DateRange): Flow<Int> = range.query(dao::observeNewArtists)

    override fun observeTrackStats(trackName: String, artistName: String): Flow<ItemStats> = combine(
        dao.observeTrackTotals(trackName, artistName),
        dao.observeTrackMonthly(trackName, artistName),
    ) { totals, monthly -> totals.toStats(monthly) }

    override fun observeArtistStats(artistName: String): Flow<ItemStats> = combine(
        dao.observeArtistTotals(artistName),
        dao.observeArtistMonthly(artistName),
    ) { totals, monthly -> totals.toStats(monthly) }

    override fun observeAlbumStats(albumName: String, artistName: String): Flow<ItemStats> = combine(
        dao.observeAlbumTotals(albumName, artistName),
        dao.observeAlbumMonthly(albumName, artistName),
    ) { totals, monthly -> totals.toStats(monthly) }

    override fun observeHistoryStart(): Flow<Instant?> = dao.observeEarliest().map { it?.let(Instant::ofEpochMilli) }

    override fun observeEventCount(): Flow<Int> = dao.observeCount()

    /**
     * `recently-played` only returns the last 50 plays, hence the periodic background
     * sync. It does not report how long a track was played; Spotify only lists plays
     * longer than ~30 s, so the track duration is used as the best estimate.
     */
    override suspend fun syncRecentlyPlayed(): DataResult<Int> {
        if (appState.demoMode.first()) return DataResult.Success(0)
        val page = when (val result = network.getRecentlyPlayed(appState.recentlyPlayedCursor()?.takeIf { it > 0 })) {
            is DataResult.Failure -> return result
            is DataResult.Success -> result.data
        }
        val tracks = page.items.mapNotNull { it.track.toEntity() }.distinctBy { it.id }
        catalogDao.upsertTracks(tracks)
        val events = page.items.mapNotNull { item ->
            val playedAt = runCatching { Instant.parse(item.playedAt).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val track = item.track
            val artist = track.artists.firstOrNull()?.name ?: return@mapNotNull null
            PlayEventEntity(
                trackId = track.id,
                trackName = track.name,
                artistName = artist,
                albumName = track.album.name,
                imageUrl = track.album.images.firstOrNull()?.url,
                playedAt = playedAt,
                msPlayed = track.durationMs,
                source = PlaySource.SYNC.name,
                skipped = null,
                shuffle = null,
                platform = null,
                dedupeKey = PlayEventEntity.dedupeKeyOf(track.name, artist, playedAt),
            )
        }
        val inserted = dao.insertAll(events).count { it != -1L }
        val cursor = page.cursors?.after?.toLongOrNull() ?: events.maxOfOrNull { it.playedAt }
        cursor?.let { appState.setRecentlyPlayedCursor(it) }
        appState.markSyncSuccess(clock.instant())
        return DataResult.Success(inserted)
    }

    private val DateRange.startMs get() = start.toEpochMilli()
    private val DateRange.endMs get() = endExclusive.toEpochMilli()

    private fun <T> DateRange.query(block: (Long, Long) -> Flow<T>): Flow<T> = block(startMs, endMs)

    private companion object {
        const val PAGE_SIZE = 50
        const val SUNDAY = 7
    }
}
