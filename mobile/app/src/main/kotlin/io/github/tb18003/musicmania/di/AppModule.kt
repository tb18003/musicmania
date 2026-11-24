/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.tb18003.musicmania.BuildConfig
import io.github.tb18003.musicmania.core.common.config.CloudConfig
import io.github.tb18003.musicmania.core.common.config.SpotifyConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** The only place build-time secrets enter the object graph. */
    @Provides
    @Singleton
    fun providesSpotifyConfig(): SpotifyConfig = SpotifyConfig(
        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
        redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
    )

    @Provides
    @Singleton
    fun providesCloudConfig(): CloudConfig = CloudConfig(apiBaseUrl = BuildConfig.MUSICMANIA_API_URL)
}
