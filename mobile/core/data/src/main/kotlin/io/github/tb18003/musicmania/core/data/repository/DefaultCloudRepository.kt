/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.repository

import io.github.tb18003.musicmania.core.common.config.CloudConfig
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.common.result.map
import io.github.tb18003.musicmania.core.data.demo.DemoLeaderboard
import io.github.tb18003.musicmania.core.datastore.prefs.AppStateDataSource
import io.github.tb18003.musicmania.core.domain.repository.CloudRepository
import io.github.tb18003.musicmania.core.domain.repository.SyncObserver
import io.github.tb18003.musicmania.core.domain.usecase.PublishListeningStatsUseCase
import io.github.tb18003.musicmania.core.model.AppVersion
import io.github.tb18003.musicmania.core.model.CloudProfile
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LatestRelease
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.model.RankedItem
import io.github.tb18003.musicmania.core.model.ReleaseNotes
import io.github.tb18003.musicmania.core.model.SharedPeriodStats
import io.github.tb18003.musicmania.core.network.CloudNetworkDataSource
import io.github.tb18003.musicmania.core.network.model.CloudAppVersionDto
import io.github.tb18003.musicmania.core.network.model.CloudLeaderboardEntryDto
import io.github.tb18003.musicmania.core.network.model.CloudMeDto
import io.github.tb18003.musicmania.core.network.model.CloudPeriodStatsDto
import io.github.tb18003.musicmania.core.network.model.CloudStatsUploadDto
import io.github.tb18003.musicmania.core.network.model.CloudTopItemDto
import io.github.tb18003.musicmania.core.network.model.CloudUserStatsDto
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Talks to the MusicMania cloud API. Disabled (and never touching the network) when the API URL
 * is not configured; serves fictional friends in demo mode so the screen can be explored.
 */
internal class DefaultCloudRepository @Inject constructor(
    private val network: CloudNetworkDataSource,
    private val config: CloudConfig,
    private val appState: AppStateDataSource,
    private val demo: DemoLeaderboard,
) : CloudRepository {
    override val isEnabled: Boolean get() = config.isConfigured

    // Not tied to an account (nor to demo data): anyone with the app configured can learn about updates.
    override suspend fun latestRelease(): DataResult<LatestRelease> =
        if (config.isConfigured) network.appVersion().map { it.toModel() } else DataResult.Failure(AppError.NotFound)

    override suspend fun me(): DataResult<CloudProfile> = route(demo::profile) { network.me().map { it.toModel() } }

    override suspend fun setSharing(enabled: Boolean): DataResult<CloudProfile> =
        route({ demo.setSharing(enabled) }) { network.updateSettings(enabled).map { it.toModel() } }

    override suspend fun publish(stats: List<SharedPeriodStats>): DataResult<Unit> =
        route({ DataResult.Success(Unit) }) { network.uploadStats(CloudStatsUploadDto(stats.map { it.toDto() })) }

    override suspend fun leaderboard(
        metric: LeaderboardMetric,
        period: LeaderboardPeriod,
        start: LocalDate,
    ): DataResult<List<LeaderboardEntry>> = route({ demo.leaderboard(metric) }) {
        network.leaderboard(metric.apiValue, period.apiValue, start.toString()).map { board -> board.entries.map { it.toModel() } }
    }

    override suspend fun friendStats(spotifyUserId: String, period: LeaderboardPeriod, start: LocalDate): DataResult<FriendStats> = route({
        demo.friendStats(spotifyUserId)
    }) { network.userStats(spotifyUserId, period.apiValue, start.toString()).map { it.toModel() } }

    override suspend fun deleteMyData(): DataResult<Unit> = route({ DataResult.Success(Unit) }) { network.deleteMe() }

    private suspend fun <T> route(demoResult: () -> DataResult<T>, live: suspend () -> DataResult<T>): DataResult<T> = when {
        !config.isConfigured -> DataResult.Failure(AppError.NotFound)
        appState.demoMode.first() -> demoResult()
        else -> live()
    }
}

/** Publishes the aggregates after each successful background sync (if the user opted in). */
internal class CloudSyncObserver @Inject constructor(private val publish: PublishListeningStatsUseCase) : SyncObserver {
    override suspend fun onSyncCompleted() {
        // Failures are fine to ignore: the next hourly sync publishes again.
        publish()
    }
}

// ---------------------------------------------------------------- mappers (DTO <-> model)

internal val LeaderboardMetric.apiValue: String
    get() = when (this) {
        LeaderboardMetric.MINUTES -> "minutes"
        LeaderboardMetric.STREAMS -> "streams"
        LeaderboardMetric.STREAK -> "streak"
        LeaderboardMetric.UNIQUE_ARTISTS -> "unique_artists"
    }

internal val LeaderboardPeriod.apiValue: String
    get() = when (this) {
        LeaderboardPeriod.WEEK -> "week"
        LeaderboardPeriod.MONTH -> "month"
        LeaderboardPeriod.YEAR -> "year"
        LeaderboardPeriod.ALL_TIME -> "all_time"
    }

internal fun CloudMeDto.toModel() = CloudProfile(spotifyUserId, displayName, sharingEnabled)

internal fun CloudLeaderboardEntryDto.toModel() = LeaderboardEntry(rank, spotifyUserId, displayName, avatarUrl, value, isMe)

internal fun CloudUserStatsDto.toModel() = FriendStats(
    spotifyUserId = spotifyUserId,
    displayName = displayName,
    minutes = minutes,
    streams = streams,
    uniqueArtists = uniqueArtists,
    currentStreak = currentStreak,
    topArtists = topArtists.map { it.toModel() },
    topTracks = topTracks.map { it.toModel() },
)

internal fun SharedPeriodStats.toDto() = CloudPeriodStatsDto(
    kind = period.apiValue,
    start = start.toString(),
    minutes = minutes,
    streams = streams,
    uniqueArtists = uniqueArtists,
    uniqueTracks = uniqueTracks,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    topArtists = topArtists.map { it.toDto() },
    topTracks = topTracks.map { it.toDto() },
)

/** Release notes come from our API but are still bounded: a few short lines per language and release. */
internal fun CloudAppVersionDto.toModel() = LatestRelease(
    version = version,
    releases = releases.take(MAX_RELEASES).mapNotNull { release ->
        AppVersion.parse(release.version)?.let { parsed ->
            ReleaseNotes(
                parsed,
                release.notes.mapValues { (_, lines) ->
                    lines.filter(String::isNotBlank).take(MAX_NOTE_LINES).map { it.take(MAX_NOTE_LENGTH) }
                },
            )
        }
    },
)

private const val MAX_RELEASES = 10
private const val MAX_NOTE_LINES = 6
private const val MAX_NOTE_LENGTH = 140

/** A friend's cover is kept only if it is HTTPS. */
internal fun CloudTopItemDto.toModel() = RankedItem(name, RankedItem.safeImageUrl(imageUrl))

/** Only an HTTPS cover is sent; the API rejects the whole upload otherwise. */
internal fun RankedItem.toDto() = CloudTopItemDto(name = name, imageUrl = RankedItem.safeImageUrl(imageUrl))
