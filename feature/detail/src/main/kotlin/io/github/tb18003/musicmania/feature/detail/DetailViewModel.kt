/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.domain.usecase.ObserveAlbumDetailUseCase
import io.github.tb18003.musicmania.core.domain.usecase.ObserveArtistDetailUseCase
import io.github.tb18003.musicmania.core.domain.usecase.ObserveTrackDetailUseCase
import io.github.tb18003.musicmania.core.model.ItemStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class DetailKind(val key: String) {
    TRACK("track"),
    ARTIST("artist"),
    ALBUM("album"),
    ;

    companion object {
        fun from(key: String?): DetailKind = entries.firstOrNull { it.key == key } ?: TRACK
    }
}

data class DetailUiState(
    val kind: DetailKind,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val sharedKeyArtist: String,
    val stats: ItemStats? = null,
    val genres: List<String> = emptyList(),
    val releaseYear: String? = null,
    val spotifyUri: String? = null,
)

/** One ViewModel for the three detail flavours; the route's `kind` picks the use case. */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeTrack: ObserveTrackDetailUseCase,
    observeArtist: ObserveArtistDetailUseCase,
    observeAlbum: ObserveAlbumDetailUseCase,
) : ViewModel() {
    private val kind = DetailKind.from(savedStateHandle.get<String>(ARG_KIND))
    private val id: String? = savedStateHandle[ARG_ID]
    private val name: String = savedStateHandle[ARG_NAME] ?: ""
    private val artist: String = savedStateHandle[ARG_ARTIST] ?: ""
    private val imageUrl: String? = savedStateHandle[ARG_IMAGE]

    private val initial = DetailUiState(
        kind = kind,
        title = name,
        subtitle = artist,
        imageUrl = imageUrl,
        sharedKeyArtist = if (kind == DetailKind.ARTIST) "" else artist,
    )

    val uiState: StateFlow<DetailUiState> = when (kind) {
        DetailKind.TRACK -> observeTrack(id, name, artist).map { detail ->
            val track = detail.metadata
            initial.copy(
                imageUrl = track?.album?.imageUrl ?: imageUrl,
                subtitle = track?.artistNames ?: artist,
                stats = detail.stats,
                releaseYear = track?.album?.releaseDate?.take(YEAR_LENGTH),
                spotifyUri = track?.spotifyUri,
            )
        }
        DetailKind.ARTIST -> observeArtist(id, name).map { detail ->
            val metadata = detail.metadata
            initial.copy(
                imageUrl = metadata?.imageUrl ?: imageUrl,
                stats = detail.stats,
                genres = metadata?.genres.orEmpty(),
                spotifyUri = metadata?.spotifyUri,
            )
        }
        DetailKind.ALBUM -> observeAlbum(id, name, artist).map { detail ->
            val album = detail.metadata
            initial.copy(
                imageUrl = album?.imageUrl ?: imageUrl,
                stats = detail.stats,
                releaseYear = album?.releaseDate?.take(YEAR_LENGTH),
                spotifyUri = album?.spotifyUri,
            )
        }
    }.stateInScope()

    private fun Flow<DetailUiState>.stateInScope() = stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_ID = "id"
        const val ARG_NAME = "name"
        const val ARG_ARTIST = "artist"
        const val ARG_IMAGE = "imageUrl"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val YEAR_LENGTH = 4
    }
}
