/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.tb18003.musicmania.feature.auth.AuthGateway
import io.github.tb18003.musicmania.feature.auth.SpotifyAuthGateway

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthFeatureModule {
    @Binds
    abstract fun bindsGateway(impl: SpotifyAuthGateway): AuthGateway
}
