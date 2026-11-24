/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.repository

import androidx.paging.PagingData
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.model.Album
import io.github.tb18003.musicmania.core.model.AlbumPlayCount
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.ArtistPlayCount
import io.github.tb18003.musicmania.core.model.DailyListening
import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.ItemStats
import io.github.tb18003.musicmania.core.model.ListeningClock
import io.github.tb18003.musicmania.core.model.ListeningSummary
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.PlaybackHabits
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.TopList
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.model.TrackPlayCount
import io.github.tb18003.musicmania.core.model.UserPreferences
import io.github.tb18003.musicmania.core.model.UserProfile
import io.github.tb18003.musicmania.core.model.WeekdayHourCell
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream
import java.time.Instant

/*
 * Repository contracts. They live in the domain layer (dependency inversion):
 * `core:data` implements them, features and use cases only see these interfaces.
 */

interface SessionRepository {
    val session: Flow<SessionState>

    suspend fun enterDemoMode()

    /** Clears tokens and local data. */
    suspend fun signOut()
}

interface UserRepository {
    val profile: Flow<UserProfile?>

    suspend fun refresh(): DataResult<Unit>
}

/** Spotify `/me/top` rankings, cached locally as daily snapshots (offline-first). */
interface TopItemsRepository {
    fun observeTopTracks(range: TimeRange): Flow<TopList<Track>>

    fun observeTopArtists(range: TimeRange): Flow<TopList<Artist>>

    suspend fun refresh(range: TimeRange): DataResult<Unit>
}

interface PlaybackRepository {
    suspend fun currentlyPlaying(): DataResult<NowPlaying?>
}

/** Local listening history built from background sync and imported data exports. */
@Suppress("TooManyFunctions")
interface HistoryRepository {
    fun pagedHistory(): Flow<PagingData<PlayEvent>>

    fun observeSummary(range: DateRange): Flow<ListeningSummary>

    fun observeTopTracks(range: DateRange, limit: Int): Flow<List<TrackPlayCount>>

    fun observeTopArtists(range: DateRange, limit: Int): Flow<List<ArtistPlayCount>>

    fun observeTopAlbums(range: DateRange, limit: Int): Flow<List<AlbumPlayCount>>

    fun observeDaily(range: DateRange): Flow<List<DailyListening>>

    fun observeClock(range: DateRange): Flow<ListeningClock>

    fun observeWeekdayHour(range: DateRange): Flow<List<WeekdayHourCell>>

    fun observeHabits(range: DateRange): Flow<PlaybackHabits>

    /** Number of artists whose first-ever play falls inside [range]. */
    fun observeNewArtists(range: DateRange): Flow<Int>

    fun observeTrackStats(trackName: String, artistName: String): Flow<ItemStats>

    fun observeArtistStats(artistName: String): Flow<ItemStats>

    fun observeAlbumStats(albumName: String, artistName: String): Flow<ItemStats>

    fun observeHistoryStart(): Flow<Instant?>

    fun observeEventCount(): Flow<Int>

    /** Pulls `/me/player/recently-played` since the last cursor. Returns inserted plays. */
    suspend fun syncRecentlyPlayed(): DataResult<Int>
}

interface CatalogRepository {
    suspend fun getTrack(id: String): DataResult<Track>

    suspend fun getArtist(id: String): DataResult<Artist>

    suspend fun getAlbum(id: String): DataResult<Album>
}

interface PlaylistRepository {
    /** Creates a private playlist and returns its Spotify URI. */
    suspend fun createPrivatePlaylist(name: String, description: String, trackUris: List<String>): DataResult<String>
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setReduceMotion(enabled: Boolean)
}

interface DataManagementRepository {
    /** Writes the local history as JSON to [output]. Returns the number of exported plays. */
    suspend fun exportHistory(output: OutputStream): DataResult<Int>

    suspend fun deleteAllLocalData()
}
