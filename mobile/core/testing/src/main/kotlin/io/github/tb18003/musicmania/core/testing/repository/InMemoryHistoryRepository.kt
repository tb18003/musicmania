/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.testing.repository

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.model.AlbumPlayCount
import io.github.tb18003.musicmania.core.model.ArtistPlayCount
import io.github.tb18003.musicmania.core.model.DailyListening
import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.ItemStats
import io.github.tb18003.musicmania.core.model.ListeningClock
import io.github.tb18003.musicmania.core.model.ListeningSummary
import io.github.tb18003.musicmania.core.model.MonthlyCount
import io.github.tb18003.musicmania.core.model.PlatformShare
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.PlaybackHabits
import io.github.tb18003.musicmania.core.model.TrackPlayCount
import io.github.tb18003.musicmania.core.model.WeekdayHourCell
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Behavioural fake: a faithful in-memory implementation of the history queries, so
 * use-case and ViewModel tests exercise realistic aggregation without Room.
 */
@Suppress("TooManyFunctions")
class InMemoryHistoryRepository(private val zone: ZoneId = ZoneOffset.UTC) : HistoryRepository {
    private val events = MutableStateFlow<List<PlayEvent>>(emptyList())
    var syncResult: DataResult<Int> = DataResult.Success(0)

    fun setEvents(list: List<PlayEvent>) = events.update { list }

    private fun inRange(range: DateRange) = events.map { all ->
        all.filter { !it.playedAt.isBefore(range.start) && it.playedAt.isBefore(range.endExclusive) }
    }

    override fun pagedHistory(): Flow<PagingData<PlayEvent>> = events.map { list ->
        val complete = LoadState.NotLoading(endOfPaginationReached = true)
        PagingData.from(list.sortedByDescending { it.playedAt }, LoadStates(complete, complete, complete))
    }

    override fun observeSummary(range: DateRange) = inRange(range).map { list ->
        ListeningSummary(
            listenedMs = list.sumOf { it.msPlayed },
            streams = list.count { it.msPlayed >= STREAM_THRESHOLD_MS },
            uniqueTracks = list.map { it.trackName to it.artistName }.distinct().size,
            uniqueArtists = list.map { it.artistName }.distinct().size,
        )
    }

    override fun observeTopTracks(range: DateRange, limit: Int) = inRange(range).map { list ->
        list.groupBy { it.trackName to it.artistName }
            .map { (key, plays) ->
                TrackPlayCount(
                    plays.first().trackId,
                    key.first,
                    key.second,
                    plays.first().albumName,
                    plays.firstNotNullOfOrNull { it.imageUrl },
                    plays.size,
                    plays.sumOf {
                        it.msPlayed
                    },
                )
            }
            .sortedWith(compareByDescending<TrackPlayCount> { it.streams }.thenByDescending { it.listenedMs })
            .take(limit)
    }

    override fun observeTopArtists(range: DateRange, limit: Int) = inRange(range).map { list ->
        list.groupBy { it.artistName }
            .map { (name, plays) ->
                ArtistPlayCount(name, plays.firstNotNullOfOrNull { it.imageUrl }, plays.size, plays.sumOf { it.msPlayed })
            }
            .sortedWith(compareByDescending<ArtistPlayCount> { it.streams }.thenByDescending { it.listenedMs })
            .take(limit)
    }

    override fun observeTopAlbums(range: DateRange, limit: Int) = inRange(range).map { list ->
        list.filter { it.albumName != null }
            .groupBy { it.albumName!! to it.artistName }
            .map { (key, plays) -> AlbumPlayCount(key.first, key.second, null, plays.size, plays.sumOf { it.msPlayed }) }
            .sortedByDescending { it.streams }
            .take(limit)
    }

    override fun observeDaily(range: DateRange) = inRange(range).map { list ->
        list.groupBy { it.playedAt.atZone(zone).toLocalDate() }
            .map { (date, plays) -> DailyListening(date, plays.sumOf { it.msPlayed }, plays.size) }
            .sortedBy { it.date }
    }

    override fun observeClock(range: DateRange) = inRange(range).map { list ->
        val buckets = LongArray(ListeningClock.HOURS)
        list.forEach { buckets[it.playedAt.atZone(zone).hour] += it.msPlayed }
        ListeningClock(buckets.toList())
    }

    override fun observeWeekdayHour(range: DateRange) = inRange(range).map { list ->
        list.groupBy { it.playedAt.atZone(zone).let { z -> z.dayOfWeek to z.hour } }
            .map { (key, plays) -> WeekdayHourCell(key.first, key.second, plays.sumOf { it.msPlayed }) }
    }

    override fun observeHabits(range: DateRange) = inRange(range).map { list ->
        val withSkip = list.mapNotNull { it.skipped }
        val withShuffle = list.mapNotNull { it.shuffle }
        PlaybackHabits(
            skipRate = withSkip.takeIf { it.isNotEmpty() }?.let { s -> s.count { it }.toDouble() / s.size },
            shuffleRate = withShuffle.takeIf { it.isNotEmpty() }?.let { s -> s.count { it }.toDouble() / s.size },
            platforms = list.filter { it.platform != null }.groupBy { it.platform!! }
                .map { (p, plays) -> PlatformShare(p, plays.sumOf { it.msPlayed }) }
                .sortedByDescending { it.listenedMs },
        )
    }

    override fun observeNewArtists(range: DateRange) = events.map { all ->
        all.groupBy { it.artistName }.values.count { plays ->
            val first = plays.minOf { it.playedAt }
            !first.isBefore(range.start) && first.isBefore(range.endExclusive)
        }
    }

    override fun observeTrackStats(trackName: String, artistName: String) = events.map { all ->
        stats(
            all.filter {
                it.trackName == trackName &&
                    it.artistName == artistName
            },
        )
    }

    override fun observeArtistStats(artistName: String) = events.map { all -> stats(all.filter { it.artistName == artistName }) }

    override fun observeAlbumStats(albumName: String, artistName: String) = events.map { all ->
        stats(
            all.filter {
                it.albumName == albumName &&
                    it.artistName == artistName
            },
        )
    }

    override fun observeHistoryStart(): Flow<Instant?> = events.map { list -> list.minOfOrNull { it.playedAt } }

    override fun observeEventCount(): Flow<Int> = events.map { it.size }

    override suspend fun syncRecentlyPlayed(): DataResult<Int> = syncResult

    private fun stats(plays: List<PlayEvent>): ItemStats {
        if (plays.isEmpty()) return ItemStats.Empty
        return ItemStats(
            streams = plays.size,
            listenedMs = plays.sumOf { it.msPlayed },
            firstPlayed = plays.minOf { it.playedAt },
            lastPlayed = plays.maxOf { it.playedAt },
            monthly = plays.groupBy { YearMonth.from(it.playedAt.atZone(zone)) }
                .map { (month, list) -> MonthlyCount(month, list.size) }
                .sortedBy { it.month },
        )
    }

    private companion object {
        const val STREAM_THRESHOLD_MS = 30_000L
    }
}
