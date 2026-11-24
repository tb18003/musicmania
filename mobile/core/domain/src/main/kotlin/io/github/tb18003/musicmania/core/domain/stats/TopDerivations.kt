/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.stats

import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.Genre
import io.github.tb18003.musicmania.core.model.TopAlbum
import io.github.tb18003.musicmania.core.model.Track

/**
 * Spotify has no "top albums" or "top genres" endpoint. They are derived from the
 * ranked tracks/artists with a Borda count: an item at rank r out of n scores n - r + 1.
 */
object TopDerivations {
    fun albumsFromTracks(tracks: List<Track>): List<TopAlbum> {
        val n = tracks.size
        return tracks
            .withIndex()
            .groupBy { (_, track) -> track.album.id ?: "${track.album.name}|${track.primaryArtistName}" }
            .map { (_, entries) ->
                val first = entries.first().value
                TopAlbum(
                    album = first.album,
                    artistName = first.primaryArtistName,
                    score = entries.sumOf { (index, _) -> (n - index).toDouble() },
                )
            }
            .sortedByDescending { it.score }
    }

    fun genresFromArtists(artists: List<Artist>): List<Genre> {
        val n = artists.size
        val scores = mutableMapOf<String, Double>()
        val counts = mutableMapOf<String, Int>()
        artists.forEachIndexed { index, artist ->
            artist.genres.distinct().forEach { genre ->
                scores[genre] = (scores[genre] ?: 0.0) + (n - index)
                counts[genre] = (counts[genre] ?: 0) + 1
            }
        }
        val max = scores.values.maxOrNull() ?: return emptyList()
        return scores.entries
            .map { (genre, score) -> Genre(genre, score / max, counts.getValue(genre)) }
            .sortedWith(compareByDescending<Genre> { it.score }.thenBy { it.name })
    }
}
