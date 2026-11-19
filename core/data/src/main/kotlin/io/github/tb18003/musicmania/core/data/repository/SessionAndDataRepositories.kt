/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.repository

import io.github.tb18003.musicmania.core.common.di.Dispatcher
import io.github.tb18003.musicmania.core.common.di.MusicManiaDispatchers
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.data.demo.DemoDataSeeder
import io.github.tb18003.musicmania.core.database.MusicManiaDatabase
import io.github.tb18003.musicmania.core.database.dao.PlayEventDao
import io.github.tb18003.musicmania.core.datastore.prefs.AppStateDataSource
import io.github.tb18003.musicmania.core.datastore.token.TokenStore
import io.github.tb18003.musicmania.core.domain.repository.DataManagementRepository
import io.github.tb18003.musicmania.core.domain.repository.SessionRepository
import io.github.tb18003.musicmania.core.model.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject

internal class DefaultSessionRepository @Inject constructor(
    private val tokenStore: TokenStore,
    private val appState: AppStateDataSource,
    private val seeder: DemoDataSeeder,
    private val dataManagement: DefaultDataManagementRepository,
    private val userRepository: DefaultUserRepository,
) : SessionRepository {
    override val session: Flow<SessionState> = combine(
        tokenStore.tokens,
        tokenStore.sessionExpired,
        appState.demoMode,
    ) { tokens, expired, demo ->
        when {
            demo -> SessionState.Demo
            tokens != null -> SessionState.SignedIn
            expired -> SessionState.Expired
            else -> SessionState.SignedOut
        }
    }.onStart { emit(SessionState.Loading) }.distinctUntilChanged()

    override suspend fun enterDemoMode() {
        seeder.seed()
        appState.setDemoMode(true)
    }

    /**
     * Signing out wipes tokens *and* the local history: the next account to sign in
     * on this device must never see the previous user's listening data.
     */
    override suspend fun signOut() {
        tokenStore.clear(sessionExpired = false)
        dataManagement.deleteAllLocalData()
        appState.clear()
        userRepository.clear()
    }
}

@Serializable
private data class ExportedPlay(
    val playedAt: String,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val msPlayed: Long,
    val spotifyTrackId: String?,
    val source: String,
    val skipped: Boolean?,
    val shuffle: Boolean?,
    val platform: String?,
)

internal class DefaultDataManagementRepository @Inject constructor(
    private val db: MusicManiaDatabase,
    private val dao: PlayEventDao,
    private val appState: AppStateDataSource,
    @param:Dispatcher(MusicManiaDispatchers.IO) private val io: CoroutineDispatcher,
) : DataManagementRepository {
    private val json = Json { explicitNulls = false }

    /** Streams the history page by page so exports of large imports stay memory-bounded. */
    override suspend fun exportHistory(output: OutputStream): DataResult<Int> = withContext(io) {
        try {
            var count = 0
            output.bufferedWriter().apply {
                write("[")
                var offset = 0
                while (true) {
                    val page = dao.page(limit = EXPORT_PAGE, offset = offset)
                    if (page.isEmpty()) break
                    page.forEach { e ->
                        if (count > 0) write(",")
                        val exported = ExportedPlay(
                            playedAt = Instant.ofEpochMilli(e.playedAt).toString(),
                            trackName = e.trackName,
                            artistName = e.artistName,
                            albumName = e.albumName,
                            msPlayed = e.msPlayed,
                            spotifyTrackId = e.trackId,
                            source = e.source,
                            skipped = e.skipped,
                            shuffle = e.shuffle,
                            platform = e.platform,
                        )
                        write(json.encodeToString(ExportedPlay.serializer(), exported))
                        count++
                    }
                    offset += page.size
                }
                write("]")
                flush()
            }
            DataResult.Success(count)
        } catch (_: IOException) {
            DataResult.Failure(AppError.Storage)
        }
    }

    override suspend fun deleteAllLocalData() {
        withContext(io) { db.clearAllTables() }
        appState.setRecentlyPlayedCursor(0)
    }

    private companion object {
        const val EXPORT_PAGE = 1_000
    }
}
