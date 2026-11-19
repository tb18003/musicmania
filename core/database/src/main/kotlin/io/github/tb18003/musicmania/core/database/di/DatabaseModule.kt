/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.tb18003.musicmania.core.database.MusicManiaDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): MusicManiaDatabase =
        Room.databaseBuilder(context, MusicManiaDatabase::class.java, MusicManiaDatabase.NAME).build()

    @Provides
    fun providesCatalogDao(db: MusicManiaDatabase) = db.catalogDao()

    @Provides
    fun providesTopSnapshotDao(db: MusicManiaDatabase) = db.topSnapshotDao()

    @Provides
    fun providesPlayEventDao(db: MusicManiaDatabase) = db.playEventDao()
}
