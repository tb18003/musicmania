/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.SyncObserver
import io.github.tb18003.musicmania.core.domain.usecase.SyncListeningDataUseCase

/**
 * Periodically captures `recently-played` (Spotify only keeps the last 50 plays) and
 * refreshes the daily top snapshots used for ranking movement.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncListeningData: SyncListeningDataUseCase,
    private val observers: Set<@JvmSuppressWildcards SyncObserver>,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val result = syncListeningData()) {
        is DataResult.Success -> {
            observers.forEach { runCatching { it.onSyncCompleted() } }
            Result.success()
        }
        is DataResult.Failure -> result.error.toWorkResult(runAttemptCount)
    }

    companion object {
        const val MAX_ATTEMPTS = 5

        /** Transient problems are retried with backoff; auth problems are not. */
        fun AppError.toWorkResult(attempt: Int): Result = when (this) {
            AppError.Network, is AppError.RateLimited, is AppError.Server ->
                if (attempt < MAX_ATTEMPTS) Result.retry() else Result.failure()
            else -> Result.failure()
        }
    }
}
