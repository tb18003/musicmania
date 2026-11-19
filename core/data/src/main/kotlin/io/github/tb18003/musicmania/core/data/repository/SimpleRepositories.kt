/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.repository

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.common.result.map
import io.github.tb18003.musicmania.core.data.demo.DemoPlayback
import io.github.tb18003.musicmania.core.data.mapper.toEntity
import io.github.tb18003.musicmania.core.data.mapper.toModel
import io.github.tb18003.musicmania.core.database.dao.CatalogDao
import io.github.tb18003.musicmania.core.datastore.prefs.AppStateDataSource
import io.github.tb18003.musicmania.core.datastore.prefs.UserPreferencesDataSource
import io.github.tb18003.musicmania.core.domain.repository.CatalogRepository
import io.github.tb18003.musicmania.core.domain.repository.PlaybackRepository
import io.github.tb18003.musicmania.core.domain.repository.PlaylistRepository
import io.github.tb18003.musicmania.core.domain.repository.PreferencesRepository
import io.github.tb18003.musicmania.core.domain.repository.UserRepository
import io.github.tb18003.musicmania.core.model.Album
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.model.UserPreferences
import io.github.tb18003.musicmania.core.model.UserProfile
import io.github.tb18003.musicmania.core.network.SpotifyNetworkDataSource
import io.github.tb18003.musicmania.core.network.model.CreatePlaylistRequest
import io.github.tb18003.musicmania.core.network.model.bestUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

internal class DefaultPlaybackRepository @Inject constructor(
    private val network: SpotifyNetworkDataSource,
    private val appState: AppStateDataSource,
    private val demoPlayback: DemoPlayback,
    private val clock: Clock,
) : PlaybackRepository {
    override suspend fun currentlyPlaying(): DataResult<NowPlaying?> {
        if (appState.demoMode.first()) return DataResult.Success(demoPlayback.current())
        return network.getCurrentlyPlaying().map { dto ->
            val item = dto?.item ?: return@map null
            val track = item.toEntity()?.toModel() ?: return@map null
            NowPlaying(track = track, progressMs = dto.progressMs ?: 0, isPlaying = dto.isPlaying, fetchedAt = clock.instant())
        }
    }
}

/** Cache-first: Room, then network with write-through. Batch endpoints are unavailable. */
internal class CachedCatalogRepository @Inject constructor(
    private val network: SpotifyNetworkDataSource,
    private val dao: CatalogDao,
    private val appState: AppStateDataSource,
) : CatalogRepository {
    override suspend fun getTrack(id: String): DataResult<Track> = cached(
        local = { dao.getTrack(id)?.toModel() },
        remote = { network.getTrack(id).map { dto -> dto.toEntity()?.also { dao.upsertTracks(listOf(it)) }?.toModel() } },
    )

    override suspend fun getArtist(id: String): DataResult<Artist> = cached(
        local = { dao.getArtist(id)?.toModel() },
        remote = { network.getArtist(id).map { dto -> dto.toEntity().also { dao.upsertArtists(listOf(it)) }.toModel() } },
    )

    override suspend fun getAlbum(id: String): DataResult<Album> = cached(
        local = { dao.getAlbum(id)?.toModel() },
        remote = { network.getAlbum(id).map { dto -> dto.toEntity().also { dao.upsertAlbums(listOf(it)) }.toModel() } },
    )

    private suspend fun <T> cached(local: suspend () -> T?, remote: suspend () -> DataResult<T?>): DataResult<T> {
        local()?.let { return DataResult.Success(it) }
        if (appState.demoMode.first()) return DataResult.Failure(AppError.NotFound)
        return when (val result = remote()) {
            is DataResult.Failure -> result
            is DataResult.Success -> result.data?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.NotFound)
        }
    }
}

@Singleton
internal class DefaultUserRepository @Inject constructor(
    private val network: SpotifyNetworkDataSource,
    private val appState: AppStateDataSource,
) : UserRepository {
    private val state = MutableStateFlow<UserProfile?>(null)
    override val profile: Flow<UserProfile?> = state.asStateFlow()

    override suspend fun refresh(): DataResult<Unit> {
        if (appState.demoMode.first()) {
            state.value = UserProfile(id = "demo", displayName = null, imageUrl = null)
            return DataResult.Success(Unit)
        }
        return network.getMe().map { dto ->
            state.value = UserProfile(id = dto.id, displayName = dto.displayName, imageUrl = dto.images.bestUrl(targetPx = 160))
        }
    }

    fun clear() {
        state.value = null
    }
}

internal class DefaultPlaylistRepository @Inject constructor(
    private val network: SpotifyNetworkDataSource,
    private val appState: AppStateDataSource,
) : PlaylistRepository {
    override suspend fun createPrivatePlaylist(name: String, description: String, trackUris: List<String>): DataResult<String> {
        if (appState.demoMode.first()) return DataResult.Failure(AppError.InvalidData("demo"))
        return network.createPlaylistWithItems(CreatePlaylistRequest(name = name, description = description), trackUris).map { it.uri }
    }
}

internal class DefaultPreferencesRepository @Inject constructor(private val dataSource: UserPreferencesDataSource) :
    PreferencesRepository {
    override val preferences: Flow<UserPreferences> = dataSource.preferences

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataSource.setThemeMode(mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataSource.setDynamicColor(enabled)
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        dataSource.setReduceMotion(enabled)
    }
}
