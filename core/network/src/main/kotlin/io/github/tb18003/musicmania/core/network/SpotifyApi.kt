/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network

import io.github.tb18003.musicmania.core.network.model.AddItemsRequest
import io.github.tb18003.musicmania.core.network.model.AlbumDto
import io.github.tb18003.musicmania.core.network.model.ArtistDto
import io.github.tb18003.musicmania.core.network.model.CreatePlaylistRequest
import io.github.tb18003.musicmania.core.network.model.CurrentlyPlayingDto
import io.github.tb18003.musicmania.core.network.model.CursorPagingDto
import io.github.tb18003.musicmania.core.network.model.PagingDto
import io.github.tb18003.musicmania.core.network.model.PlayHistoryDto
import io.github.tb18003.musicmania.core.network.model.PlaylistDto
import io.github.tb18003.musicmania.core.network.model.SnapshotDto
import io.github.tb18003.musicmania.core.network.model.TrackDto
import io.github.tb18003.musicmania.core.network.model.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Spotify Web API endpoints available to Development Mode apps (post February 2026).
 * Batch endpoints (`GET /tracks?ids=`) are no longer available: fetch individually.
 */
internal interface SpotifyApi {
    @GET("me")
    suspend fun getMe(): UserDto

    @GET("me/top/tracks")
    suspend fun getTopTracks(
        @Query("time_range") timeRange: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<TrackDto>

    @GET("me/top/artists")
    suspend fun getTopArtists(
        @Query("time_range") timeRange: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<ArtistDto>

    @GET("me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Query("limit") limit: Int = 50,
        @Query("after") afterEpochMs: Long? = null,
    ): CursorPagingDto<PlayHistoryDto>

    /** 204 No Content when nothing is playing, hence the raw [Response]. */
    @GET("me/player/currently-playing")
    suspend fun getCurrentlyPlaying(): Response<CurrentlyPlayingDto>

    @GET("tracks/{id}")
    suspend fun getTrack(@Path("id") id: String): TrackDto

    @GET("artists/{id}")
    suspend fun getArtist(@Path("id") id: String): ArtistDto

    @GET("albums/{id}")
    suspend fun getAlbum(@Path("id") id: String): AlbumDto

    @POST("me/playlists")
    suspend fun createPlaylist(@Body body: CreatePlaylistRequest): PlaylistDto

    @POST("playlists/{id}/items")
    suspend fun addPlaylistItems(@Path("id") playlistId: String, @Body body: AddItemsRequest): SnapshotDto
}
