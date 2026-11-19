/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.testing.repository

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.CatalogRepository
import io.github.tb18003.musicmania.core.domain.repository.DataManagementRepository
import io.github.tb18003.musicmania.core.domain.repository.ImportSource
import io.github.tb18003.musicmania.core.domain.repository.PlaybackRepository
import io.github.tb18003.musicmania.core.domain.repository.PlaylistRepository
import io.github.tb18003.musicmania.core.domain.repository.PreferencesRepository
import io.github.tb18003.musicmania.core.domain.repository.SessionRepository
import io.github.tb18003.musicmania.core.domain.repository.StreamingHistoryImporter
import io.github.tb18003.musicmania.core.domain.repository.SyncScheduler
import io.github.tb18003.musicmania.core.domain.repository.TopItemsRepository
import io.github.tb18003.musicmania.core.domain.repository.UserRepository
import io.github.tb18003.musicmania.core.model.Album
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.ImportProgress
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.SyncStatus
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopList
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.model.UserPreferences
import io.github.tb18003.musicmania.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.OutputStream

class FakeTopItemsRepository : TopItemsRepository {
    private val tracks = MutableStateFlow<Map<TimeRange, TopList<Track>>>(emptyMap())
    private val artists = MutableStateFlow<Map<TimeRange, TopList<Artist>>>(emptyMap())
    val refreshedRanges = mutableListOf<TimeRange>()
    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)

    fun setTracks(range: TimeRange, list: TopList<Track>) = tracks.update { it + (range to list) }

    fun setArtists(range: TimeRange, list: TopList<Artist>) = artists.update { it + (range to list) }

    override fun observeTopTracks(range: TimeRange): Flow<TopList<Track>> = tracks.map { it[range] }.filterNotNull()

    override fun observeTopArtists(range: TimeRange): Flow<TopList<Artist>> = artists.map { it[range] }.filterNotNull()

    override suspend fun refresh(range: TimeRange): DataResult<Unit> {
        refreshedRanges += range
        return refreshResult
    }
}

class FakePlaybackRepository : PlaybackRepository {
    val responses = ArrayDeque<DataResult<NowPlaying?>>()
    var default: DataResult<NowPlaying?> = DataResult.Success(null)

    override suspend fun currentlyPlaying(): DataResult<NowPlaying?> = responses.removeFirstOrNull() ?: default
}

class FakeCatalogRepository : CatalogRepository {
    val tracks = mutableMapOf<String, Track>()
    val artists = mutableMapOf<String, Artist>()
    val albums = mutableMapOf<String, Album>()

    override suspend fun getTrack(id: String) = tracks[id].toResult()

    override suspend fun getArtist(id: String) = artists[id].toResult()

    override suspend fun getAlbum(id: String) = albums[id].toResult()

    private fun <T> T?.toResult(): DataResult<T> = this?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.NotFound)
}

class FakeSessionRepository(initial: SessionState = SessionState.SignedIn) : SessionRepository {
    val state = MutableStateFlow(initial)
    override val session: Flow<SessionState> = state

    override suspend fun enterDemoMode() = state.update { SessionState.Demo }

    override suspend fun signOut() = state.update { SessionState.SignedOut }
}

class FakeUserRepository : UserRepository {
    val state = MutableStateFlow<UserProfile?>(UserProfile("user", "Test User", null))
    override val profile: Flow<UserProfile?> = state

    override suspend fun refresh(): DataResult<Unit> = DataResult.Success(Unit)
}

class FakePreferencesRepository : PreferencesRepository {
    val state = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = state

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(useDynamicColor = enabled) }

    override suspend fun setReduceMotion(enabled: Boolean) = state.update { it.copy(reduceMotion = enabled) }
}

class FakePlaylistRepository : PlaylistRepository {
    var lastUris: List<String>? = null
    var result: DataResult<String> = DataResult.Success("spotify:playlist:new")

    override suspend fun createPrivatePlaylist(name: String, description: String, trackUris: List<String>): DataResult<String> {
        lastUris = trackUris
        return result
    }
}

class FakeSyncScheduler : SyncScheduler {
    val state = MutableStateFlow(SyncStatus(lastSuccessfulSync = null, isRunning = false))
    var syncNowRequests = 0
    var periodicScheduled = false
    override val status: Flow<SyncStatus> = state

    override fun schedulePeriodicSync() {
        periodicScheduled = true
    }

    override fun requestSyncNow() {
        syncNowRequests++
    }

    override fun cancelAll() {
        periodicScheduled = false
    }
}

class FakeDataManagementRepository : DataManagementRepository {
    var deleted = false

    override suspend fun exportHistory(output: OutputStream): DataResult<Int> {
        output.write("[]".toByteArray())
        return DataResult.Success(0)
    }

    override suspend fun deleteAllLocalData() {
        deleted = true
    }
}

class FakeStreamingHistoryImporter(private val progress: List<ImportProgress> = emptyList()) : StreamingHistoryImporter {
    var lastSources: List<ImportSource>? = null

    override fun import(sources: List<ImportSource>): Flow<ImportProgress> {
        lastSources = sources
        return progress.asFlow()
    }
}
