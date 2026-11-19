/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.tb18003.musicmania.core.datastore.di.AppStateStore
import io.github.tb18003.musicmania.core.datastore.di.UserPreferencesStore
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesDataSource @Inject constructor(@param:UserPreferencesStore private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            useDynamicColor = prefs[DYNAMIC_COLOR] ?: false,
            reduceMotion = prefs[REDUCE_MOTION] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun setReduceMotion(enabled: Boolean) = dataStore.edit { it[REDUCE_MOTION] = enabled }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }
}

/** Non-sensitive app state: demo flag and sync cursors. */
@Singleton
class AppStateDataSource @Inject constructor(@param:AppStateStore private val dataStore: DataStore<Preferences>) {
    val demoMode: Flow<Boolean> = dataStore.data.map { it[DEMO_MODE] ?: false }.distinctUntilChanged()

    val lastSuccessfulSync: Flow<Instant?> = dataStore.data.map { prefs -> prefs[LAST_SYNC]?.let(Instant::ofEpochMilli) }

    suspend fun setDemoMode(enabled: Boolean) = dataStore.edit { it[DEMO_MODE] = enabled }

    suspend fun recentlyPlayedCursor(): Long? = dataStore.data.first()[RECENTLY_PLAYED_CURSOR]

    suspend fun setRecentlyPlayedCursor(epochMs: Long) = dataStore.edit { it[RECENTLY_PLAYED_CURSOR] = epochMs }

    suspend fun markSyncSuccess(at: Instant) = dataStore.edit { it[LAST_SYNC] = at.toEpochMilli() }

    suspend fun clear() = dataStore.edit { it.clear() }

    private companion object {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val RECENTLY_PLAYED_CURSOR = longPreferencesKey("recently_played_cursor")
        val LAST_SYNC = longPreferencesKey("last_successful_sync")
    }
}
