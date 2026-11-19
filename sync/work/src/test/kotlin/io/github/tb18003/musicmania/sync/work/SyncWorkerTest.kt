/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.SyncObserver
import io.github.tb18003.musicmania.core.domain.usecase.RefreshTopItemsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.SyncListeningDataUseCase
import io.github.tb18003.musicmania.core.testing.repository.FakeTopItemsRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import io.github.tb18003.musicmania.sync.work.SyncWorker.Companion.toWorkResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val history = InMemoryHistoryRepository()
    private var notified = 0
    private val observer = SyncObserver { notified++ }

    private fun worker(): SyncWorker = TestListenableWorkerBuilder<SyncWorker>(context)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SyncWorker(
                    appContext,
                    workerParameters,
                    SyncListeningDataUseCase(history, RefreshTopItemsUseCase(FakeTopItemsRepository())),
                    setOf(observer),
                )
            },
        )
        .build()

    @Test
    fun successNotifiesObservers() = runTest {
        assertIs<ListenableWorker.Result.Success>(worker().doWork())
        assertEquals(1, notified)
    }

    @Test
    fun transientErrorsRetryAndAuthErrorsFail() = runTest {
        history.syncResult = DataResult.Failure(AppError.Network)
        assertIs<ListenableWorker.Result.Retry>(worker().doWork())

        history.syncResult = DataResult.Failure(AppError.Unauthorized)
        assertIs<ListenableWorker.Result.Failure>(worker().doWork())
        assertEquals(0, notified)
    }

    @Test
    fun retriesAreBounded() {
        assertIs<ListenableWorker.Result.Failure>(AppError.Network.toWorkResult(SyncWorker.MAX_ATTEMPTS))
        assertIs<ListenableWorker.Result.Retry>(AppError.RateLimited(10).toWorkResult(0))
    }
}
