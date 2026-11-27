/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.network.model.CloudAppVersionDto
import io.github.tb18003.musicmania.core.network.model.CloudLeaderboardDto
import io.github.tb18003.musicmania.core.network.model.CloudMeDto
import io.github.tb18003.musicmania.core.network.model.CloudSettingsDto
import io.github.tb18003.musicmania.core.network.model.CloudStatsUploadDto
import io.github.tb18003.musicmania.core.network.model.CloudUserStatsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** Retrofit description of the MusicMania cloud API (api/openapi.yaml). */
interface MusicManiaCloudApi {
    /** Public: the latest release, to tell the user when the app is out of date. */
    @GET("v1/app/version")
    suspend fun appVersion(): CloudAppVersionDto

    @GET("v1/me")
    suspend fun me(): CloudMeDto

    @PUT("v1/me/settings")
    suspend fun updateSettings(@Body body: CloudSettingsDto): CloudMeDto

    @PUT("v1/me/stats")
    suspend fun uploadStats(@Body body: CloudStatsUploadDto)

    @DELETE("v1/me")
    suspend fun deleteMe()

    @GET("v1/leaderboards")
    suspend fun leaderboard(
        @Query("metric") metric: String,
        @Query("period") period: String,
        @Query("start") start: String,
    ): CloudLeaderboardDto

    @GET("v1/users/{spotifyUserId}/stats")
    suspend fun userStats(
        @Path("spotifyUserId") spotifyUserId: String,
        @Query("period") period: String,
        @Query("start") start: String,
    ): CloudUserStatsDto
}

/** Network boundary for the cloud API; faked in data-layer tests. */
interface CloudNetworkDataSource {
    suspend fun appVersion(): DataResult<CloudAppVersionDto>

    suspend fun me(): DataResult<CloudMeDto>

    suspend fun updateSettings(sharingEnabled: Boolean): DataResult<CloudMeDto>

    suspend fun uploadStats(upload: CloudStatsUploadDto): DataResult<Unit>

    suspend fun deleteMe(): DataResult<Unit>

    suspend fun leaderboard(metric: String, period: String, start: String): DataResult<CloudLeaderboardDto>

    suspend fun userStats(spotifyUserId: String, period: String, start: String): DataResult<CloudUserStatsDto>
}

internal class RetrofitCloudNetworkDataSource @Inject constructor(private val api: MusicManiaCloudApi) : CloudNetworkDataSource {
    override suspend fun appVersion() = cloudCall { api.appVersion() }

    override suspend fun me() = cloudCall { api.me() }

    override suspend fun updateSettings(sharingEnabled: Boolean) = cloudCall { api.updateSettings(CloudSettingsDto(sharingEnabled)) }

    override suspend fun uploadStats(upload: CloudStatsUploadDto) = cloudCall { api.uploadStats(upload) }

    override suspend fun deleteMe() = cloudCall { api.deleteMe() }

    override suspend fun leaderboard(metric: String, period: String, start: String) = cloudCall { api.leaderboard(metric, period, start) }

    override suspend fun userStats(spotifyUserId: String, period: String, start: String) =
        cloudCall { api.userStats(spotifyUserId, period, start) }

    /** Like [safeApiCall], but a 403 from our API always means "not on the allowlist". */
    private suspend inline fun <T> cloudCall(crossinline block: suspend () -> T): DataResult<T> = when (val result = safeApiCall(block)) {
        is DataResult.Failure -> if (result.error == AppError.Forbidden) DataResult.Failure(AppError.NotAllowlisted) else result
        is DataResult.Success -> result
    }
}
