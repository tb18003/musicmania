/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.network.SpotifyNetworkDataSource
import io.github.tb18003.musicmania.core.network.SpotifyTimeRange
import io.github.tb18003.musicmania.core.network.model.AlbumDto
import io.github.tb18003.musicmania.core.network.model.ArtistDto
import io.github.tb18003.musicmania.core.network.model.CreatePlaylistRequest
import io.github.tb18003.musicmania.core.network.model.CurrentlyPlayingDto
import io.github.tb18003.musicmania.core.network.model.CursorPagingDto
import io.github.tb18003.musicmania.core.network.model.PlayHistoryDto
import io.github.tb18003.musicmania.core.network.model.PlaylistDto
import io.github.tb18003.musicmania.core.network.model.SimplifiedAlbumDto
import io.github.tb18003.musicmania.core.network.model.SimplifiedArtistDto
import io.github.tb18003.musicmania.core.network.model.TrackDto
import io.github.tb18003.musicmania.core.network.model.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class MutableClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}

fun trackDto(id: String, name: String = "Track $id", artist: String = "Artist") = TrackDto(
    id = id,
    name = name,
    artists = listOf(SimplifiedArtistDto(id = "a-$artist", name = artist)),
    album = SimplifiedAlbumDto(id = "al-$id", name = "Album $id"),
    durationMs = 200_000,
    uri = "spotify:track:$id",
)

fun artistDto(id: String) = ArtistDto(id = id, name = "Artist $id", genres = listOf("pop"))

class FakeNetworkDataSource : SpotifyNetworkDataSource {
    var topTracks: DataResult<List<TrackDto>> = DataResult.Success(emptyList())
    var topArtists: DataResult<List<ArtistDto>> = DataResult.Success(emptyList())
    var recentlyPlayed: DataResult<CursorPagingDto<PlayHistoryDto>> = DataResult.Success(CursorPagingDto())
    val recentlyPlayedCursors = mutableListOf<Long?>()

    override suspend fun getMe(): DataResult<UserDto> = DataResult.Success(UserDto("me"))

    override suspend fun getTopTracks(range: SpotifyTimeRange, limit: Int) = topTracks

    override suspend fun getTopArtists(range: SpotifyTimeRange, limit: Int) = topArtists

    override suspend fun getRecentlyPlayed(afterEpochMs: Long?): DataResult<CursorPagingDto<PlayHistoryDto>> {
        recentlyPlayedCursors += afterEpochMs
        return recentlyPlayed
    }

    override suspend fun getCurrentlyPlaying(): DataResult<CurrentlyPlayingDto?> = DataResult.Success(null)

    override suspend fun getTrack(id: String): DataResult<TrackDto> = DataResult.Success(trackDto(id))

    override suspend fun getArtist(id: String): DataResult<ArtistDto> = DataResult.Success(artistDto(id))

    override suspend fun getAlbum(id: String): DataResult<AlbumDto> = DataResult.Failure(AppError.NotFound)

    override suspend fun createPlaylistWithItems(request: CreatePlaylistRequest, uris: List<String>) =
        DataResult.Success(PlaylistDto("p", "spotify:playlist:p"))
}
