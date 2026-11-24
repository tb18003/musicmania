/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.tb18003.musicmania.core.database.model.AlbumCountRow
import io.github.tb18003.musicmania.core.database.model.ArtistCountRow
import io.github.tb18003.musicmania.core.database.model.DayRow
import io.github.tb18003.musicmania.core.database.model.HabitsRow
import io.github.tb18003.musicmania.core.database.model.HourRow
import io.github.tb18003.musicmania.core.database.model.ItemTotalsRow
import io.github.tb18003.musicmania.core.database.model.MonthRow
import io.github.tb18003.musicmania.core.database.model.PlatformRow
import io.github.tb18003.musicmania.core.database.model.PlayEventEntity
import io.github.tb18003.musicmania.core.database.model.SummaryRow
import io.github.tb18003.musicmania.core.database.model.TrackCountRow
import io.github.tb18003.musicmania.core.database.model.WeekdayHourRow
import kotlinx.coroutines.flow.Flow

/**
 * Aggregations run in SQLite (not in Kotlin) so lifetime statistics over hundreds of
 * thousands of imported plays stay fast and memory-friendly. A "stream" is a play of
 * at least 30 seconds, matching Spotify's own definition.
 * Time bucketing uses SQLite's `localtime` modifier, which respects DST transitions.
 */
@Suppress("TooManyFunctions")
@Dao
interface PlayEventDao {
    /** Returns the row id per event, or -1 when ignored as a duplicate. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<PlayEventEntity>): List<Long>

    @Query("SELECT * FROM play_events ORDER BY playedAt DESC")
    fun pagingSource(): PagingSource<Int, PlayEventEntity>

    @Query("SELECT * FROM play_events ORDER BY playedAt ASC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<PlayEventEntity>

    @Query("SELECT COUNT(*) FROM play_events")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(playedAt) FROM play_events")
    fun observeEarliest(): Flow<Long?>

    @Query("DELETE FROM play_events")
    suspend fun deleteAll()

    @Query(
        """
        SELECT COALESCE(SUM(msPlayed), 0) AS listenedMs,
               COALESCE(SUM(CASE WHEN msPlayed >= $STREAM_MS THEN 1 ELSE 0 END), 0) AS streams,
               COUNT(DISTINCT trackName || '|' || artistName) AS uniqueTracks,
               COUNT(DISTINCT artistName) AS uniqueArtists
        FROM play_events WHERE playedAt >= :start AND playedAt < :end
        """,
    )
    fun observeSummary(start: Long, end: Long): Flow<SummaryRow>

    @Query(
        """
        SELECT g.trackId, g.trackName, g.artistName, g.albumName,
               COALESCE(g.imageUrl, (SELECT t.imageUrl FROM tracks t WHERE t.id = g.trackId)) AS imageUrl,
               g.streams, g.listenedMs
        FROM (
            SELECT MAX(trackId) AS trackId, trackName, artistName, MAX(albumName) AS albumName, MAX(imageUrl) AS imageUrl,
                   COUNT(*) AS streams, SUM(msPlayed) AS listenedMs
            FROM play_events
            WHERE playedAt >= :start AND playedAt < :end AND msPlayed >= $STREAM_MS
            GROUP BY trackName, artistName
            ORDER BY streams DESC, listenedMs DESC
            LIMIT :limit
        ) g
        ORDER BY g.streams DESC, g.listenedMs DESC
        """,
    )
    fun observeTopTracks(start: Long, end: Long, limit: Int): Flow<List<TrackCountRow>>

    @Query(
        """
        SELECT p.artistName, (SELECT a.imageUrl FROM artists a WHERE a.name = p.artistName LIMIT 1) AS imageUrl,
               COUNT(*) AS streams, SUM(p.msPlayed) AS listenedMs
        FROM play_events p
        WHERE p.playedAt >= :start AND p.playedAt < :end AND p.msPlayed >= $STREAM_MS
        GROUP BY p.artistName
        ORDER BY streams DESC, listenedMs DESC
        LIMIT :limit
        """,
    )
    fun observeTopArtists(start: Long, end: Long, limit: Int): Flow<List<ArtistCountRow>>

    @Query(
        """
        SELECT albumName, artistName, MAX(imageUrl) AS imageUrl, COUNT(*) AS streams, SUM(msPlayed) AS listenedMs
        FROM play_events
        WHERE playedAt >= :start AND playedAt < :end AND msPlayed >= $STREAM_MS AND albumName IS NOT NULL
        GROUP BY albumName, artistName
        ORDER BY streams DESC, listenedMs DESC
        LIMIT :limit
        """,
    )
    fun observeTopAlbums(start: Long, end: Long, limit: Int): Flow<List<AlbumCountRow>>

    @Query(
        """
        SELECT date(playedAt / 1000, 'unixepoch', 'localtime') AS day, SUM(msPlayed) AS listenedMs, COUNT(*) AS streams
        FROM play_events WHERE playedAt >= :start AND playedAt < :end
        GROUP BY day ORDER BY day
        """,
    )
    fun observeDaily(start: Long, end: Long): Flow<List<DayRow>>

    @Query(
        """
        SELECT CAST(strftime('%H', playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, SUM(msPlayed) AS listenedMs
        FROM play_events WHERE playedAt >= :start AND playedAt < :end
        GROUP BY hour
        """,
    )
    fun observeHourly(start: Long, end: Long): Flow<List<HourRow>>

    @Query(
        """
        SELECT CAST(strftime('%w', playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday,
               CAST(strftime('%H', playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               SUM(msPlayed) AS listenedMs
        FROM play_events WHERE playedAt >= :start AND playedAt < :end
        GROUP BY weekday, hour
        """,
    )
    fun observeWeekdayHour(start: Long, end: Long): Flow<List<WeekdayHourRow>>

    @Query(
        """
        SELECT COUNT(skipped) AS skipKnown, COALESCE(SUM(skipped), 0) AS skipped,
               COUNT(shuffle) AS shuffleKnown, COALESCE(SUM(shuffle), 0) AS shuffled
        FROM play_events WHERE playedAt >= :start AND playedAt < :end
        """,
    )
    fun observeHabits(start: Long, end: Long): Flow<HabitsRow>

    @Query(
        """
        SELECT platform, SUM(msPlayed) AS listenedMs FROM play_events
        WHERE playedAt >= :start AND playedAt < :end AND platform IS NOT NULL
        GROUP BY platform ORDER BY listenedMs DESC
        """,
    )
    fun observePlatforms(start: Long, end: Long): Flow<List<PlatformRow>>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT artistName FROM play_events GROUP BY artistName
            HAVING MIN(playedAt) >= :start AND MIN(playedAt) < :end
        )
        """,
    )
    fun observeNewArtists(start: Long, end: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) AS streams, COALESCE(SUM(msPlayed), 0) AS listenedMs, MIN(playedAt) AS firstPlayed, MAX(playedAt) AS lastPlayed
        FROM play_events WHERE trackName = :trackName AND artistName = :artistName AND msPlayed >= $STREAM_MS
        """,
    )
    fun observeTrackTotals(trackName: String, artistName: String): Flow<ItemTotalsRow>

    @Query(
        """
        SELECT strftime('%Y-%m', playedAt / 1000, 'unixepoch', 'localtime') AS month, COUNT(*) AS streams
        FROM play_events WHERE trackName = :trackName AND artistName = :artistName AND msPlayed >= $STREAM_MS
        GROUP BY month ORDER BY month
        """,
    )
    fun observeTrackMonthly(trackName: String, artistName: String): Flow<List<MonthRow>>

    @Query(
        """
        SELECT COUNT(*) AS streams, COALESCE(SUM(msPlayed), 0) AS listenedMs, MIN(playedAt) AS firstPlayed, MAX(playedAt) AS lastPlayed
        FROM play_events WHERE artistName = :artistName AND msPlayed >= $STREAM_MS
        """,
    )
    fun observeArtistTotals(artistName: String): Flow<ItemTotalsRow>

    @Query(
        """
        SELECT strftime('%Y-%m', playedAt / 1000, 'unixepoch', 'localtime') AS month, COUNT(*) AS streams
        FROM play_events WHERE artistName = :artistName AND msPlayed >= $STREAM_MS
        GROUP BY month ORDER BY month
        """,
    )
    fun observeArtistMonthly(artistName: String): Flow<List<MonthRow>>

    @Query(
        """
        SELECT COUNT(*) AS streams, COALESCE(SUM(msPlayed), 0) AS listenedMs, MIN(playedAt) AS firstPlayed, MAX(playedAt) AS lastPlayed
        FROM play_events WHERE albumName = :albumName AND artistName = :artistName AND msPlayed >= $STREAM_MS
        """,
    )
    fun observeAlbumTotals(albumName: String, artistName: String): Flow<ItemTotalsRow>

    @Query(
        """
        SELECT strftime('%Y-%m', playedAt / 1000, 'unixepoch', 'localtime') AS month, COUNT(*) AS streams
        FROM play_events WHERE albumName = :albumName AND artistName = :artistName AND msPlayed >= $STREAM_MS
        GROUP BY month ORDER BY month
        """,
    )
    fun observeAlbumMonthly(albumName: String, artistName: String): Flow<List<MonthRow>>

    companion object {
        const val STREAM_MS = 30_000L
    }
}
