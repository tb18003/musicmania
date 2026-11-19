/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.tb18003.musicmania.core.common.di.ApplicationScope
import io.github.tb18003.musicmania.core.common.di.Dispatcher
import io.github.tb18003.musicmania.core.common.di.MusicManiaDispatchers
import io.github.tb18003.musicmania.core.datastore.crypto.Encryptor
import io.github.tb18003.musicmania.core.datastore.crypto.TinkEncryptor
import io.github.tb18003.musicmania.core.datastore.token.AndroidBase64Codec
import io.github.tb18003.musicmania.core.datastore.token.Base64Codec
import io.github.tb18003.musicmania.core.datastore.token.EncryptedTokenStore
import io.github.tb18003.musicmania.core.datastore.token.TokenStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecureSessionStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferencesStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppStateStore

/** File names are referenced by the backup exclusion rules in the app manifest. */
object DataStoreFiles {
    const val SECURE_SESSION = "secure_session"
    const val USER_PREFERENCES = "user_preferences"
    const val APP_STATE = "app_state"
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    @SecureSessionStore
    fun providesSecureSessionStore(
        @ApplicationContext context: Context,
        @Dispatcher(MusicManiaDispatchers.IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = create(context, DataStoreFiles.SECURE_SESSION, scope + io)

    @Provides
    @Singleton
    @UserPreferencesStore
    fun providesUserPreferencesStore(
        @ApplicationContext context: Context,
        @Dispatcher(MusicManiaDispatchers.IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = create(context, DataStoreFiles.USER_PREFERENCES, scope + io)

    @Provides
    @Singleton
    @AppStateStore
    fun providesAppStateStore(
        @ApplicationContext context: Context,
        @Dispatcher(MusicManiaDispatchers.IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = create(context, DataStoreFiles.APP_STATE, scope + io)

    private fun create(context: Context, name: String, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(name) },
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityBindingsModule {
    @Binds
    abstract fun bindsEncryptor(impl: TinkEncryptor): Encryptor

    @Binds
    abstract fun bindsTokenStore(impl: EncryptedTokenStore): TokenStore

    @Binds
    abstract fun bindsBase64(impl: AndroidBase64Codec): Base64Codec
}
