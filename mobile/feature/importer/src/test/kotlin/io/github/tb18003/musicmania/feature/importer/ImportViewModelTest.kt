/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.importer

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.domain.repository.ImportException
import io.github.tb18003.musicmania.core.domain.repository.ImportSource
import io.github.tb18003.musicmania.core.domain.repository.StreamingHistoryImporter
import io.github.tb18003.musicmania.core.domain.usecase.ImportStreamingHistoryUseCase
import io.github.tb18003.musicmania.core.model.ImportProgress
import io.github.tb18003.musicmania.core.model.ImportResult
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.repository.FakeStreamingHistoryImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val source = object : ImportSource {
        override val displayName = "export.zip"

        override fun openStream() = "".byteInputStream()
    }

    @Test
    fun `completed import exposes the result`() {
        val result = ImportResult(1, 10, 8, 2, null, null)
        val importer = FakeStreamingHistoryImporter(listOf(ImportProgress.Running("a.json", 0, 5), ImportProgress.Completed(result)))
        val viewModel = ImportViewModel(ImportStreamingHistoryUseCase(importer))

        viewModel.import(listOf(source))

        assertEquals(ImportUiState.Done(result), viewModel.uiState.value)
        assertEquals(listOf(source), importer.lastSources)
    }

    @Test
    fun `import errors are mapped to typed failures`() {
        val failing = object : StreamingHistoryImporter {
            override fun import(sources: List<ImportSource>): Flow<ImportProgress> = flow {
                throw ImportException(AppError.InvalidData("bad"))
            }
        }
        val viewModel = ImportViewModel(ImportStreamingHistoryUseCase(failing))

        viewModel.import(listOf(source))

        assertEquals(ImportUiState.Failed(AppError.InvalidData("bad")), viewModel.uiState.value)
        viewModel.reset()
        assertEquals(ImportUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `empty selection is ignored`() {
        val viewModel = ImportViewModel(ImportStreamingHistoryUseCase(FakeStreamingHistoryImporter()))
        viewModel.import(emptyList())
        assertEquals(ImportUiState.Idle, viewModel.uiState.value)
    }
}
