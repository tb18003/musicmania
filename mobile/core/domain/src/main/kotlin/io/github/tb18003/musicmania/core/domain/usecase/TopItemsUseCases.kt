/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.repository.TopItemsRepository
import io.github.tb18003.musicmania.core.domain.stats.RankMovementCalculator
import io.github.tb18003.musicmania.core.domain.stats.TopDerivations
import io.github.tb18003.musicmania.core.domain.stats.toDateRange
import io.github.tb18003.musicmania.core.model.AlbumSummary
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.ArtistSummary
import io.github.tb18003.musicmania.core.model.Genre
import io.github.tb18003.musicmania.core.model.Ranked
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopAlbum
import io.github.tb18003.musicmania.core.model.TopList
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.model.TrackPlayCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

const val TOP_LIST_SIZE = 50

/** Stable key used to compare an item between ranking snapshots. */
internal fun Track.rankKey(): String = id ?: "$name|$primaryArtistName"

internal fun Artist.rankKey(): String = id ?: name

internal fun <T> TopList<T>.toRanked(key: (T) -> String): List<Ranked<T>> {
    val movements = RankMovementCalculator.compute(items.map(key), previousRanks)
    return items.mapIndexed { index, item -> Ranked(rank = index + 1, item = item, movement = movements[index]) }
}

internal fun TrackPlayCount.toTrack(): Track = Track(
    id = trackId,
    name = trackName,
    artists = listOf(ArtistSummary(id = null, name = artistName)),
    album = AlbumSummary(id = null, name = albumName.orEmpty(), imageUrl = imageUrl),
    durationMs = 0,
)

/**
 * Strategy selection: API-backed ranges come from Spotify's `/me/top` snapshots,
 * [TimeRange.LIFETIME] is computed from the local listening history.
 */
class GetTopTracksUseCase @Inject constructor(
    private val topItems: TopItemsRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(range: TimeRange): Flow<List<Ranked<Track>>> = if (range.isApiBacked) {
        topItems.observeTopTracks(range).map { it.toRanked { track -> track.rankKey() } }
    } else {
        history.observeTopTracks(range.toDateRange(clock), TOP_LIST_SIZE).map { plays ->
            plays.mapIndexed { index, play ->
                Ranked(rank = index + 1, item = play.toTrack(), streams = play.streams, listenedMs = play.listenedMs)
            }
        }
    }
}

class GetTopArtistsUseCase @Inject constructor(
    private val topItems: TopItemsRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(range: TimeRange): Flow<List<Ranked<Artist>>> = if (range.isApiBacked) {
        topItems.observeTopArtists(range).map { it.toRanked { artist -> artist.rankKey() } }
    } else {
        history.observeTopArtists(range.toDateRange(clock), TOP_LIST_SIZE).map { plays ->
            plays.mapIndexed { index, play ->
                Ranked(
                    rank = index + 1,
                    item = Artist(id = null, name = play.artistName, imageUrl = play.imageUrl),
                    streams = play.streams,
                    listenedMs = play.listenedMs,
                )
            }
        }
    }
}

class GetTopAlbumsUseCase @Inject constructor(
    private val topItems: TopItemsRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(range: TimeRange): Flow<List<Ranked<TopAlbum>>> = if (range.isApiBacked) {
        topItems.observeTopTracks(range).map { list ->
            TopDerivations.albumsFromTracks(list.items).mapIndexed { index, album -> Ranked(index + 1, album) }
        }
    } else {
        history.observeTopAlbums(range.toDateRange(clock), TOP_LIST_SIZE).map { plays ->
            plays.mapIndexed { index, play ->
                Ranked(
                    rank = index + 1,
                    item = TopAlbum(
                        album = AlbumSummary(id = null, name = play.albumName, imageUrl = play.imageUrl),
                        artistName = play.artistName,
                        score = play.streams.toDouble(),
                    ),
                    streams = play.streams,
                    listenedMs = play.listenedMs,
                )
            }
        }
    }
}

/** Genres are only exposed on artists, so lifetime falls back to the ~1 year ranking. */
class GetTopGenresUseCase @Inject constructor(private val topItems: TopItemsRepository) {
    operator fun invoke(range: TimeRange): Flow<List<Genre>> {
        val apiRange = if (range.isApiBacked) range else TimeRange.ONE_YEAR
        return topItems.observeTopArtists(apiRange).map { TopDerivations.genresFromArtists(it.items) }
    }
}

class RefreshTopItemsUseCase @Inject constructor(private val topItems: TopItemsRepository) {
    suspend operator fun invoke(range: TimeRange): DataResult<Unit> =
        if (range.isApiBacked) topItems.refresh(range) else DataResult.Success(Unit)

    suspend fun refreshAll(): DataResult<Unit> = TimeRange.entries.filter { it.isApiBacked }
        .map { topItems.refresh(it) }
        .firstOrNull { it is DataResult.Failure } ?: DataResult.Success(Unit)
}
