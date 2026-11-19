/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.tb18003.musicmania.core.datastore.prefs.AppStateDataSource
import io.github.tb18003.musicmania.core.domain.repository.SyncObserver
import io.github.tb18003.musicmania.core.domain.repository.SyncScheduler
import io.github.tb18003.musicmania.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkManagerSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    appState: AppStateDataSource,
) : SyncScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    override val status: Flow<SyncStatus> = combine(
        appState.lastSuccessfulSync,
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK),
    ) { lastSync, infos ->
        SyncStatus(lastSuccessfulSync = lastSync, isRunning = infos.any { it.state == WorkInfo.State.RUNNING })
    }

    /** Every ~hour: frequent enough to never miss plays (50-play window), light on battery and quota. */
    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES, FLEX_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun requestSyncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(ONE_TIME_WORK)
    }

    companion object {
        const val PERIODIC_WORK = "musicmania_periodic_sync"
        const val ONE_TIME_WORK = "musicmania_sync_now"
        private const val SYNC_INTERVAL_MINUTES = 60L
        private const val FLEX_MINUTES = 15L
        private const val BACKOFF_MINUTES = 10L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {
    @Binds
    abstract fun bindsScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    /** Declares the (possibly empty) set of observers; other modules contribute with @IntoSet. */
    @Multibinds
    abstract fun syncObservers(): Set<SyncObserver>
}
