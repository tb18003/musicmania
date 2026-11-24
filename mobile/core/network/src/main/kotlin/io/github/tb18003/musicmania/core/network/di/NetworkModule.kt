/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.tb18003.musicmania.core.common.config.SpotifyConfig
import io.github.tb18003.musicmania.core.network.BuildConfig
import io.github.tb18003.musicmania.core.network.RetrofitSpotifyNetworkDataSource
import io.github.tb18003.musicmania.core.network.SpotifyApi
import io.github.tb18003.musicmania.core.network.SpotifyNetworkDataSource
import io.github.tb18003.musicmania.core.network.auth.AllowedApiHost
import io.github.tb18003.musicmania.core.network.auth.AuthInterceptor
import io.github.tb18003.musicmania.core.network.auth.SpotifyTokenRefresher
import io.github.tb18003.musicmania.core.network.auth.TokenAuthenticator
import io.github.tb18003.musicmania.core.network.auth.TokenRefresher
import io.github.tb18003.musicmania.core.network.interceptor.RateLimitInterceptor
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Bare client for the token endpoint: no auth interceptor/authenticator (avoids recursion). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TokenHttpClient

/** Unauthenticated client for images (Coil). Never carries the bearer token. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    private const val TIMEOUT_SECONDS = 20L

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun providesAllowedHost(config: SpotifyConfig): AllowedApiHost = AllowedApiHost(config.apiBaseUrl.toHttpUrl().host)

    @Provides
    @Singleton
    @PublicHttpClient
    fun providesPublicClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @TokenHttpClient
    fun providesTokenClient(@PublicHttpClient base: OkHttpClient): OkHttpClient = base.newBuilder()
        .followRedirects(false)
        .build()

    @Provides
    @Singleton
    fun providesApiClient(
        @PublicHttpClient base: OkHttpClient,
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(authInterceptor)
        .addInterceptor(RateLimitInterceptor())
        .authenticator(authenticator)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun providesSpotifyApi(client: OkHttpClient, json: Json, config: SpotifyConfig): SpotifyApi = Retrofit.Builder()
        .baseUrl(config.apiBaseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SpotifyApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindingsModule {
    @Binds
    abstract fun bindsNetworkDataSource(impl: RetrofitSpotifyNetworkDataSource): SpotifyNetworkDataSource

    @Binds
    abstract fun bindsTokenRefresher(impl: SpotifyTokenRefresher): TokenRefresher
}
