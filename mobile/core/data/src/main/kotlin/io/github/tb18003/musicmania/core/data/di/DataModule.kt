/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.tb18003.musicmania.core.data.importer.DefaultStreamingHistoryImporter
import io.github.tb18003.musicmania.core.data.repository.CachedCatalogRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultDataManagementRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultHistoryRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultPlaybackRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultPlaylistRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultPreferencesRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultSessionRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultUserRepository
import io.github.tb18003.musicmania.core.data.repository.OfflineFirstTopItemsRepository
import io.github.tb18003.musicmania.core.domain.repository.CatalogRepository
import io.github.tb18003.musicmania.core.domain.repository.DataManagementRepository
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.repository.PlaybackRepository
import io.github.tb18003.musicmania.core.domain.repository.PlaylistRepository
import io.github.tb18003.musicmania.core.domain.repository.PreferencesRepository
import io.github.tb18003.musicmania.core.domain.repository.SessionRepository
import io.github.tb18003.musicmania.core.domain.repository.StreamingHistoryImporter
import io.github.tb18003.musicmania.core.domain.repository.TopItemsRepository
import io.github.tb18003.musicmania.core.domain.repository.UserRepository

/** Binds domain contracts to their data-layer implementations (dependency inversion). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindsTopItems(impl: OfflineFirstTopItemsRepository): TopItemsRepository

    @Binds
    abstract fun bindsHistory(impl: DefaultHistoryRepository): HistoryRepository

    @Binds
    abstract fun bindsPlayback(impl: DefaultPlaybackRepository): PlaybackRepository

    @Binds
    abstract fun bindsCatalog(impl: CachedCatalogRepository): CatalogRepository

    @Binds
    abstract fun bindsUser(impl: DefaultUserRepository): UserRepository

    @Binds
    abstract fun bindsSession(impl: DefaultSessionRepository): SessionRepository

    @Binds
    abstract fun bindsPlaylist(impl: DefaultPlaylistRepository): PlaylistRepository

    @Binds
    abstract fun bindsPreferences(impl: DefaultPreferencesRepository): PreferencesRepository

    @Binds
    abstract fun bindsDataManagement(impl: DefaultDataManagementRepository): DataManagementRepository

    @Binds
    abstract fun bindsImporter(impl: DefaultStreamingHistoryImporter): StreamingHistoryImporter
}
