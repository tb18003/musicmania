/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.common.result.getOrNull
import io.github.tb18003.musicmania.core.domain.repository.CatalogRepository
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.model.Album
import io.github.tb18003.musicmania.core.model.Artist
import io.github.tb18003.musicmania.core.model.ItemStats
import io.github.tb18003.musicmania.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Metadata (from Spotify, may be null when offline or unknown) plus personal stats. */
data class ItemDetail<T>(
    val metadata: T?,
    val stats: ItemStats,
)

class ObserveTrackDetailUseCase @Inject constructor(
    private val catalog: CatalogRepository,
    private val history: HistoryRepository,
) {
    operator fun invoke(trackId: String?, trackName: String, artistName: String): Flow<ItemDetail<Track>> =
        combine(metadata(trackId) { catalog.getTrack(it) }, history.observeTrackStats(trackName, artistName), ::ItemDetail)
}

class ObserveArtistDetailUseCase @Inject constructor(
    private val catalog: CatalogRepository,
    private val history: HistoryRepository,
) {
    operator fun invoke(artistId: String?, artistName: String): Flow<ItemDetail<Artist>> =
        combine(metadata(artistId) { catalog.getArtist(it) }, history.observeArtistStats(artistName), ::ItemDetail)
}

class ObserveAlbumDetailUseCase @Inject constructor(
    private val catalog: CatalogRepository,
    private val history: HistoryRepository,
) {
    operator fun invoke(albumId: String?, albumName: String, artistName: String): Flow<ItemDetail<Album>> =
        combine(metadata(albumId) { catalog.getAlbum(it) }, history.observeAlbumStats(albumName, artistName), ::ItemDetail)
}

private fun <T> metadata(id: String?, load: suspend (String) -> DataResult<T>): Flow<T?> = flow {
    emit(null)
    if (id != null) load(id).getOrNull()?.let { emit(it) }
}
