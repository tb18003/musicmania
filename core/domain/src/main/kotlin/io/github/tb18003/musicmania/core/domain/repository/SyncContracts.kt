/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.repository

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.model.ImportProgress
import io.github.tb18003.musicmania.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface SyncScheduler {
    val status: Flow<SyncStatus>

    fun schedulePeriodicSync()

    fun requestSyncNow()

    fun cancelAll()
}

/**
 * Observer notified after every successful background sync. Implementations are
 * contributed through a Hilt multibinding (`@IntoSet`), e.g. the home-screen widget,
 * so the sync module never depends on its consumers.
 */
fun interface SyncObserver {
    suspend fun onSyncCompleted()
}

/** Abstraction over a user-picked file so the domain stays free of Android `Uri`. */
interface ImportSource {
    val displayName: String

    fun openStream(): InputStream
}

interface StreamingHistoryImporter {
    /** Emits progress; fails with [ImportException] on unrecoverable input. */
    fun import(sources: List<ImportSource>): Flow<ImportProgress>
}

class ImportException(val error: AppError, cause: Throwable? = null) : Exception(error.toString(), cause)
