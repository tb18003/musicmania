/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.repository.ImportSource
import io.github.tb18003.musicmania.core.domain.repository.PlaylistRepository
import io.github.tb18003.musicmania.core.domain.repository.StreamingHistoryImporter
import io.github.tb18003.musicmania.core.domain.repository.TopItemsRepository
import io.github.tb18003.musicmania.core.model.ImportProgress
import io.github.tb18003.musicmania.core.model.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Turns the current top tracks of a range into a private Spotify playlist. */
class CreateTopTracksPlaylistUseCase @Inject constructor(
    private val topItems: TopItemsRepository,
    private val playlists: PlaylistRepository,
) {
    suspend operator fun invoke(range: TimeRange, name: String, description: String): DataResult<String> {
        val apiRange = if (range.isApiBacked) range else TimeRange.ONE_YEAR
        val uris = topItems.observeTopTracks(apiRange).first().items.mapNotNull { it.spotifyUri }
        if (uris.isEmpty()) return DataResult.Failure(AppError.InvalidData("No tracks to add"))
        return playlists.createPrivatePlaylist(name, description, uris)
    }
}

class ImportStreamingHistoryUseCase @Inject constructor(private val importer: StreamingHistoryImporter) {
    operator fun invoke(sources: List<ImportSource>): Flow<ImportProgress> = importer.import(sources)
}

/** Background sync: accumulate recently played tracks and refresh daily top snapshots. */
class SyncListeningDataUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val refreshTopItems: RefreshTopItemsUseCase,
) {
    suspend operator fun invoke(): DataResult<Int> {
        val synced = history.syncRecentlyPlayed()
        if (synced is DataResult.Failure) return synced
        return when (val tops = refreshTopItems.refreshAll()) {
            is DataResult.Failure -> tops
            is DataResult.Success -> synced
        }
    }
}
