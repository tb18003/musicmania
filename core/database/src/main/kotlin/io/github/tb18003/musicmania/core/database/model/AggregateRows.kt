/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database.model

/** Projections returned by aggregation queries. */

data class SummaryRow(val listenedMs: Long, val streams: Int, val uniqueTracks: Int, val uniqueArtists: Int)

data class TrackCountRow(
    val trackId: String?,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val imageUrl: String?,
    val streams: Int,
    val listenedMs: Long,
)

data class ArtistCountRow(val artistName: String, val imageUrl: String?, val streams: Int, val listenedMs: Long)

data class AlbumCountRow(val albumName: String, val artistName: String, val imageUrl: String?, val streams: Int, val listenedMs: Long)

data class DayRow(val day: String, val listenedMs: Long, val streams: Int)

data class HourRow(val hour: Int, val listenedMs: Long)

data class WeekdayHourRow(val weekday: Int, val hour: Int, val listenedMs: Long)

data class HabitsRow(val skipKnown: Int, val skipped: Int, val shuffleKnown: Int, val shuffled: Int)

data class PlatformRow(val platform: String, val listenedMs: Long)

data class ItemTotalsRow(val streams: Int, val listenedMs: Long, val firstPlayed: Long?, val lastPlayed: Long?)

data class MonthRow(val month: String, val streams: Int)
