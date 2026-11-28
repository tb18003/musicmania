/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.model

import kotlinx.serialization.Serializable

/*
 * Wire models of the MusicMania cloud API (contract: api/openapi.yaml in the repository).
 */

@Serializable
data class CloudMeDto(
    val spotifyUserId: String,
    val displayName: String? = null,
    val role: String = "member",
    val sharingEnabled: Boolean = false,
)

@Serializable
data class CloudSettingsDto(val sharingEnabled: Boolean)

@Serializable
data class CloudStatsUploadDto(val periods: List<CloudPeriodStatsDto>)

@Serializable
data class CloudPeriodStatsDto(
    /** week | month | year | all_time */
    val kind: String,
    /** ISO date of the first day of the period. */
    val start: String,
    val minutes: Long,
    val streams: Int,
    val uniqueArtists: Int,
    val uniqueTracks: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val topArtists: List<CloudTopItemDto> = emptyList(),
    val topTracks: List<CloudTopItemDto> = emptyList(),
)

@Serializable
data class CloudAppVersionDto(
    val version: String,
    val releases: List<CloudReleaseNotesDto> = emptyList(),
)

@Serializable
data class CloudReleaseNotesDto(
    val version: String,
    val notes: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class CloudTopItemDto(
    val name: String,
    val spotifyId: String? = null,
    /** Public HTTPS cover URL (Spotify CDN). */
    val imageUrl: String? = null,
)

@Serializable
data class CloudLeaderboardDto(
    val metric: String,
    val period: String,
    val start: String,
    val entries: List<CloudLeaderboardEntryDto> = emptyList(),
)

@Serializable
data class CloudLeaderboardEntryDto(
    val rank: Int,
    val spotifyUserId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val value: Long,
    val isMe: Boolean = false,
)

@Serializable
data class CloudUserStatsDto(
    val spotifyUserId: String,
    val displayName: String? = null,
    val minutes: Long,
    val streams: Int,
    val uniqueArtists: Int,
    val uniqueTracks: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val topArtists: List<CloudTopItemDto> = emptyList(),
    val topTracks: List<CloudTopItemDto> = emptyList(),
)
