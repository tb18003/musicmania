/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.testing

import io.github.tb18003.musicmania.core.model.AlbumSummary
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.ArtistSummary
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.PlaySource
import io.github.tb18003.musicmania.core.model.Track
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Builders with sensible defaults so tests only state what matters. */
object TestData {
    val NOW: Instant = Instant.parse("2026-06-15T12:00:00Z")
    val fixedClock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    fun track(
        id: String = "t1",
        name: String = "Track $id",
        artist: String = "Artist",
        albumId: String? = "al-$id",
        albumName: String = "Album $id",
    ) = Track(
        id = id,
        name = name,
        artists = listOf(ArtistSummary(id = "a-$artist", name = artist)),
        album = AlbumSummary(id = albumId, name = albumName, imageUrl = null),
        durationMs = 200_000,
        spotifyUri = "spotify:track:$id",
    )

    fun artist(id: String = "a1", name: String = "Artist $id", genres: List<String> = emptyList()) =
        Artist(id = id, name = name, imageUrl = null, genres = genres, spotifyUri = "spotify:artist:$id")

    fun play(
        track: String = "Song",
        artist: String = "Artist",
        album: String? = "Album",
        playedAt: Instant = NOW,
        msPlayed: Long = 180_000,
        skipped: Boolean? = false,
        shuffle: Boolean? = false,
        platform: String? = "android",
        imageUrl: String? = null,
    ) = PlayEvent(
        trackId = null,
        trackName = track,
        artistName = artist,
        albumName = album,
        imageUrl = imageUrl,
        playedAt = playedAt,
        msPlayed = msPlayed,
        source = PlaySource.IMPORT,
        skipped = skipped,
        shuffle = shuffle,
        platform = platform,
    )
}
