/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.network.model.AddItemsRequest
import io.github.tb18003.musicmania.core.network.model.AlbumDto
import io.github.tb18003.musicmania.core.network.model.ArtistDto
import io.github.tb18003.musicmania.core.network.model.CreatePlaylistRequest
import io.github.tb18003.musicmania.core.network.model.CurrentlyPlayingDto
import io.github.tb18003.musicmania.core.network.model.CursorPagingDto
import io.github.tb18003.musicmania.core.network.model.PlayHistoryDto
import io.github.tb18003.musicmania.core.network.model.PlaylistDto
import io.github.tb18003.musicmania.core.network.model.TrackDto
import io.github.tb18003.musicmania.core.network.model.UserDto
import javax.inject.Inject
import javax.inject.Singleton

/** Spotify's `time_range` values. */
enum class SpotifyTimeRange(val apiValue: String) {
    SHORT_TERM("short_term"),
    MEDIUM_TERM("medium_term"),
    LONG_TERM("long_term"),
}

/** Network boundary used by repositories; faked in data-layer tests. */
interface SpotifyNetworkDataSource {
    suspend fun getMe(): DataResult<UserDto>

    suspend fun getTopTracks(range: SpotifyTimeRange, limit: Int = MAX_PAGE): DataResult<List<TrackDto>>

    suspend fun getTopArtists(range: SpotifyTimeRange, limit: Int = MAX_PAGE): DataResult<List<ArtistDto>>

    suspend fun getRecentlyPlayed(afterEpochMs: Long?): DataResult<CursorPagingDto<PlayHistoryDto>>

    suspend fun getCurrentlyPlaying(): DataResult<CurrentlyPlayingDto?>

    suspend fun getTrack(id: String): DataResult<TrackDto>

    suspend fun getArtist(id: String): DataResult<ArtistDto>

    suspend fun getAlbum(id: String): DataResult<AlbumDto>

    suspend fun createPlaylistWithItems(request: CreatePlaylistRequest, uris: List<String>): DataResult<PlaylistDto>

    companion object {
        const val MAX_PAGE = 50
    }
}

@Singleton
internal class RetrofitSpotifyNetworkDataSource @Inject constructor(private val api: SpotifyApi) : SpotifyNetworkDataSource {
    override suspend fun getMe() = safeApiCall { api.getMe() }

    override suspend fun getTopTracks(range: SpotifyTimeRange, limit: Int) =
        safeApiCall { api.getTopTracks(range.apiValue, limit).items.filterNot { it.isLocal || it.id == null } }

    override suspend fun getTopArtists(range: SpotifyTimeRange, limit: Int) = safeApiCall { api.getTopArtists(range.apiValue, limit).items }

    override suspend fun getRecentlyPlayed(afterEpochMs: Long?) =
        safeApiCall { api.getRecentlyPlayed(limit = SpotifyNetworkDataSource.MAX_PAGE, afterEpochMs = afterEpochMs) }

    override suspend fun getCurrentlyPlaying(): DataResult<CurrentlyPlayingDto?> = safeApiCall { api.getCurrentlyPlaying() }
        .let { result ->
            when (result) {
                is DataResult.Failure -> result
                is DataResult.Success -> {
                    val response = result.data
                    when {
                        response.isSuccessful -> DataResult.Success(response.body()?.takeIf { it.type == null || it.type == "track" })
                        else -> DataResult.Failure(httpError(response.code(), response.headers()["Retry-After"]))
                    }
                }
            }
        }

    override suspend fun getTrack(id: String) = safeApiCall { api.getTrack(id) }

    override suspend fun getArtist(id: String) = safeApiCall { api.getArtist(id) }

    override suspend fun getAlbum(id: String) = safeApiCall { api.getAlbum(id) }

    override suspend fun createPlaylistWithItems(request: CreatePlaylistRequest, uris: List<String>): DataResult<PlaylistDto> {
        if (uris.isEmpty()) return DataResult.Failure(AppError.InvalidData("Empty playlist"))
        return safeApiCall {
            val playlist = api.createPlaylist(request)
            uris.chunked(MAX_ITEMS_PER_REQUEST).forEach { chunk -> api.addPlaylistItems(playlist.id, AddItemsRequest(chunk)) }
            playlist
        }
    }

    private companion object {
        const val MAX_ITEMS_PER_REQUEST = 100
    }
}
