/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.mapper

import io.github.tb18003.musicmania.core.database.model.AlbumCountRow
import io.github.tb18003.musicmania.core.database.model.AlbumEntity
import io.github.tb18003.musicmania.core.database.model.ArtistCountRow
import io.github.tb18003.musicmania.core.database.model.ArtistEntity
import io.github.tb18003.musicmania.core.database.model.ArtistRef
import io.github.tb18003.musicmania.core.database.model.ItemTotalsRow
import io.github.tb18003.musicmania.core.database.model.MonthRow
import io.github.tb18003.musicmania.core.database.model.PlayEventEntity
import io.github.tb18003.musicmania.core.database.model.TrackCountRow
import io.github.tb18003.musicmania.core.database.model.TrackEntity
import io.github.tb18003.musicmania.core.model.Album
import io.github.tb18003.musicmania.core.model.AlbumPlayCount
import io.github.tb18003.musicmania.core.model.AlbumSummary
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.ArtistPlayCount
import io.github.tb18003.musicmania.core.model.ArtistSummary
import io.github.tb18003.musicmania.core.model.ItemStats
import io.github.tb18003.musicmania.core.model.MonthlyCount
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.PlaySource
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.model.TrackPlayCount
import io.github.tb18003.musicmania.core.network.SpotifyTimeRange
import io.github.tb18003.musicmania.core.network.model.AlbumDto
import io.github.tb18003.musicmania.core.network.model.ArtistDto
import io.github.tb18003.musicmania.core.network.model.SimplifiedArtistDto
import io.github.tb18003.musicmania.core.network.model.TrackDto
import io.github.tb18003.musicmania.core.network.model.bestUrl
import java.time.Instant
import java.time.YearMonth

internal fun TimeRange.toSpotify(): SpotifyTimeRange = when (this) {
    TimeRange.FOUR_WEEKS -> SpotifyTimeRange.SHORT_TERM
    TimeRange.SIX_MONTHS -> SpotifyTimeRange.MEDIUM_TERM
    TimeRange.ONE_YEAR, TimeRange.LIFETIME -> SpotifyTimeRange.LONG_TERM
}

// region Network -> Database

internal fun SimplifiedArtistDto.toRef() = ArtistRef(id = id, name = name)

internal fun TrackDto.toEntity(): TrackEntity? = id?.let {
    TrackEntity(
        id = it,
        name = name,
        artists = artists.map { a -> a.toRef() },
        albumId = album.id,
        albumName = album.name,
        imageUrl = album.images.bestUrl(),
        releaseDate = album.releaseDate,
        durationMs = durationMs,
        explicit = explicit,
        uri = uri,
    )
}

internal fun ArtistDto.toEntity() = ArtistEntity(id = id, name = name, imageUrl = images.bestUrl(), genres = genres, uri = uri)

internal fun AlbumDto.toEntity() = AlbumEntity(
    id = id,
    name = name,
    artists = artists.map { it.toRef() },
    imageUrl = images.bestUrl(),
    releaseDate = releaseDate,
    totalTracks = totalTracks,
    uri = uri,
)

// endregion

// region Database -> Domain

internal fun TrackEntity.toModel() = Track(
    id = id,
    name = name,
    artists = artists.map { ArtistSummary(it.id, it.name) },
    album = AlbumSummary(id = albumId, name = albumName, imageUrl = imageUrl, releaseDate = releaseDate),
    durationMs = durationMs,
    explicit = explicit,
    spotifyUri = uri,
)

internal fun ArtistEntity.toModel() = Artist(id = id, name = name, imageUrl = imageUrl, genres = genres, spotifyUri = uri)

internal fun AlbumEntity.toModel() = Album(
    id = id,
    name = name,
    artists = artists.map { ArtistSummary(it.id, it.name) },
    imageUrl = imageUrl,
    releaseDate = releaseDate,
    totalTracks = totalTracks,
    spotifyUri = uri,
)

internal fun PlayEventEntity.toModel() = PlayEvent(
    id = id,
    trackId = trackId,
    trackName = trackName,
    artistName = artistName,
    albumName = albumName,
    imageUrl = imageUrl,
    playedAt = Instant.ofEpochMilli(playedAt),
    msPlayed = msPlayed,
    source = runCatching { PlaySource.valueOf(source) }.getOrDefault(PlaySource.IMPORT),
    skipped = skipped,
    shuffle = shuffle,
    platform = platform,
)

internal fun PlayEvent.toEntity() = PlayEventEntity(
    trackId = trackId,
    trackName = trackName,
    artistName = artistName,
    albumName = albumName,
    imageUrl = imageUrl,
    playedAt = playedAt.toEpochMilli(),
    msPlayed = msPlayed,
    source = source.name,
    skipped = skipped,
    shuffle = shuffle,
    platform = platform,
    dedupeKey = PlayEventEntity.dedupeKeyOf(trackName, artistName, playedAt.toEpochMilli()),
)

internal fun TrackCountRow.toModel() = TrackPlayCount(trackId, trackName, artistName, albumName, imageUrl, streams, listenedMs)

internal fun ArtistCountRow.toModel() = ArtistPlayCount(artistName, imageUrl, streams, listenedMs)

internal fun AlbumCountRow.toModel() = AlbumPlayCount(albumName, artistName, imageUrl, streams, listenedMs)

internal fun ItemTotalsRow.toStats(monthly: List<MonthRow>) = if (streams == 0) {
    ItemStats.Empty
} else {
    ItemStats(
        streams = streams,
        listenedMs = listenedMs,
        firstPlayed = firstPlayed?.let(Instant::ofEpochMilli),
        lastPlayed = lastPlayed?.let(Instant::ofEpochMilli),
        monthly = monthly.mapNotNull { row -> runCatching { MonthlyCount(YearMonth.parse(row.month), row.streams) }.getOrNull() },
    )
}

// endregion
