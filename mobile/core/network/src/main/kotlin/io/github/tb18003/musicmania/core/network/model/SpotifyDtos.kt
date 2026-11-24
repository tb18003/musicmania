/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Spotify Web API DTOs (Adapter layer). Fields removed for Development Mode apps
 * in February 2026 (popularity, followers, label, ...) are intentionally absent.
 */

@Serializable
data class ImageDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class SimplifiedArtistDto(
    val id: String? = null,
    val name: String,
    val uri: String? = null,
)

@Serializable
data class SimplifiedAlbumDto(
    val id: String? = null,
    val name: String,
    val images: List<ImageDto> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    val uri: String? = null,
)

@Serializable
data class AlbumDto(
    val id: String,
    val name: String,
    val artists: List<SimplifiedArtistDto> = emptyList(),
    val images: List<ImageDto> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("total_tracks") val totalTracks: Int? = null,
    val uri: String? = null,
)

@Serializable
data class TrackDto(
    val id: String? = null,
    val name: String,
    val artists: List<SimplifiedArtistDto> = emptyList(),
    val album: SimplifiedAlbumDto,
    @SerialName("duration_ms") val durationMs: Long = 0,
    val explicit: Boolean = false,
    val uri: String? = null,
    @SerialName("is_local") val isLocal: Boolean = false,
)

@Serializable
data class ArtistDto(
    val id: String,
    val name: String,
    val images: List<ImageDto> = emptyList(),
    val genres: List<String> = emptyList(),
    val uri: String? = null,
)

@Serializable
data class PagingDto<T>(
    val items: List<T> = emptyList(),
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null,
)

@Serializable
data class CursorsDto(
    val after: String? = null,
    val before: String? = null,
)

@Serializable
data class CursorPagingDto<T>(
    val items: List<T> = emptyList(),
    val next: String? = null,
    val cursors: CursorsDto? = null,
    val limit: Int? = null,
)

@Serializable
data class PlayHistoryDto(
    val track: TrackDto,
    @SerialName("played_at") val playedAt: String,
)

@Serializable
data class CurrentlyPlayingDto(
    val item: TrackDto? = null,
    @SerialName("progress_ms") val progressMs: Long? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("currently_playing_type") val type: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val images: List<ImageDto> = emptyList(),
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String,
    val public: Boolean = false,
)

@Serializable
data class PlaylistDto(
    val id: String,
    val uri: String,
    val name: String? = null,
)

@Serializable
data class AddItemsRequest(val uris: List<String>)

@Serializable
data class SnapshotDto(@SerialName("snapshot_id") val snapshotId: String? = null)

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** Picks the image closest to [targetPx] wide, falling back to the first one. */
fun List<ImageDto>.bestUrl(targetPx: Int = 640): String? =
    minByOrNull { kotlin.math.abs((it.width ?: targetPx) - targetPx) }?.url ?: firstOrNull()?.url
