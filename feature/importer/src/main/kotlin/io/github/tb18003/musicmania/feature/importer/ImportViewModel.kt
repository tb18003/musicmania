/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.domain.repository.ImportException
import io.github.tb18003.musicmania.core.domain.repository.ImportSource
import io.github.tb18003.musicmania.core.domain.usecase.ImportStreamingHistoryUseCase
import io.github.tb18003.musicmania.core.model.ImportProgress
import io.github.tb18003.musicmania.core.model.ImportResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState

    data class Running(val currentFile: String, val playsRead: Int) : ImportUiState

    data class Done(val result: ImportResult) : ImportUiState

    data class Failed(val error: AppError) : ImportUiState
}

/** Adapts a Storage Access Framework [Uri] to the domain's [ImportSource]. */
class UriImportSource(private val resolver: ContentResolver, private val uri: Uri) : ImportSource {
    override val displayName: String by lazy {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }

    override fun openStream(): InputStream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
}

@HiltViewModel
class ImportViewModel @Inject constructor(private val importHistory: ImportStreamingHistoryUseCase) : ViewModel() {
    private val state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = state.asStateFlow()
    private var job: Job? = null

    fun import(sources: List<ImportSource>) {
        if (sources.isEmpty() || state.value is ImportUiState.Running) return
        job = viewModelScope.launch {
            state.value = ImportUiState.Running(sources.first().displayName, 0)
            importHistory(sources)
                .catch { e -> state.value = ImportUiState.Failed((e as? ImportException)?.error ?: AppError.Unknown(e.message)) }
                .collect { progress ->
                    state.value = when (progress) {
                        is ImportProgress.Running -> ImportUiState.Running(progress.currentFile, progress.eventsRead)
                        is ImportProgress.Completed -> ImportUiState.Done(progress.result)
                    }
                }
        }
    }

    fun cancel() {
        job?.cancel()
        state.value = ImportUiState.Idle
    }

    fun reset() {
        state.value = ImportUiState.Idle
    }
}
