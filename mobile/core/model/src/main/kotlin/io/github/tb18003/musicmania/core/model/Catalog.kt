/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

/**
 * Catalog entities. Identifiers are nullable because tracks coming from an imported
 * streaming-history file (basic format) only carry names, never Spotify IDs.
 */
data class ArtistSummary(
    val id: String?,
    val name: String,
)

data class AlbumSummary(
    val id: String?,
    val name: String,
    val imageUrl: String?,
    val releaseDate: String? = null,
)

data class Track(
    val id: String?,
    val name: String,
    val artists: List<ArtistSummary>,
    val album: AlbumSummary,
    val durationMs: Long,
    val explicit: Boolean = false,
    val spotifyUri: String? = null,
) {
    val primaryArtistName: String get() = artists.firstOrNull()?.name.orEmpty()
    val artistNames: String get() = artists.joinToString(", ") { it.name }
}

data class Artist(
    val id: String?,
    val name: String,
    val imageUrl: String?,
    val genres: List<String> = emptyList(),
    val spotifyUri: String? = null,
)

data class Album(
    val id: String?,
    val name: String,
    val artists: List<ArtistSummary>,
    val imageUrl: String?,
    val releaseDate: String?,
    val totalTracks: Int?,
    val spotifyUri: String? = null,
)

data class UserProfile(
    val id: String,
    val displayName: String?,
    val imageUrl: String?,
)
