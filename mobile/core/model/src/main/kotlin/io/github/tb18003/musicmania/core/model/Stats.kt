/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Half-open interval `[start, endExclusive)`. */
data class DateRange(
    val start: Instant,
    val endExclusive: Instant,
) {
    init {
        require(!endExclusive.isBefore(start)) { "endExclusive must not be before start" }
    }

    val lengthMs: Long get() = endExclusive.toEpochMilli() - start.toEpochMilli()

    /** The window of identical length immediately preceding this one. */
    fun previous(): DateRange = DateRange(start.minusMillis(lengthMs), start)
}

data class ListeningSummary(
    val listenedMs: Long,
    val streams: Int,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
) {
    val minutes: Long get() = listenedMs / 60_000

    companion object {
        val Empty = ListeningSummary(0, 0, 0, 0)
    }
}

data class TrackPlayCount(
    val trackId: String?,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val imageUrl: String?,
    val streams: Int,
    val listenedMs: Long,
)

data class ArtistPlayCount(
    val artistName: String,
    val imageUrl: String?,
    val streams: Int,
    val listenedMs: Long,
)

data class AlbumPlayCount(
    val albumName: String,
    val artistName: String,
    val imageUrl: String?,
    val streams: Int,
    val listenedMs: Long,
)

data class DailyListening(
    val date: LocalDate,
    val listenedMs: Long,
    val streams: Int,
)

data class WeekdayHourCell(
    val dayOfWeek: DayOfWeek,
    val hour: Int,
    val listenedMs: Long,
)

/** 24 buckets, index = local hour of day. */
data class ListeningClock(val msByHour: List<Long>) {
    init {
        require(msByHour.size == HOURS) { "ListeningClock needs exactly $HOURS buckets" }
    }

    val peakHour: Int? get() = msByHour.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index

    companion object {
        const val HOURS = 24
        val Empty = ListeningClock(List(HOURS) { 0L })
    }
}

data class Streaks(
    val current: Int,
    val longest: Int,
    val longestEndedOn: LocalDate?,
)

data class PlaybackHabits(
    /** Fraction 0..1 of plays that were skipped, null when the data source does not report it. */
    val skipRate: Double?,
    val shuffleRate: Double?,
    val platforms: List<PlatformShare>,
)

data class PlatformShare(
    val platform: String,
    val listenedMs: Long,
)

data class PeriodComparison(
    val current: ListeningSummary,
    val previous: ListeningSummary,
) {
    /** Relative change of listening time, null when there is no previous data. */
    val listenedChange: Double?
        get() = if (previous.listenedMs == 0L) null else (current.listenedMs - previous.listenedMs).toDouble() / previous.listenedMs
}

enum class MilestoneType { MINUTES, STREAMS, ARTISTS, STREAK_DAYS }

data class Milestone(
    val type: MilestoneType,
    val threshold: Long,
    val value: Long,
) {
    val achieved: Boolean get() = value >= threshold
    val progress: Float get() = (value.toFloat() / threshold).coerceIn(0f, 1f)
}

data class MonthlyCount(
    val month: YearMonth,
    val streams: Int,
)

/** Personal statistics for a single track, artist or album. */
data class ItemStats(
    val streams: Int,
    val listenedMs: Long,
    val firstPlayed: Instant?,
    val lastPlayed: Instant?,
    val monthly: List<MonthlyCount>,
) {
    companion object {
        val Empty = ItemStats(0, 0, null, null, emptyList())
    }
}

enum class ListeningPersonality {
    EARLY_BIRD,
    DAYTIME_CRUISER,
    EVENING_DRIFTER,
    NIGHT_OWL,
    ;

    companion object {
        @Suppress("MagicNumber")
        fun fromPeakHour(hour: Int?): ListeningPersonality? = when (hour) {
            null -> null
            in 5..10 -> EARLY_BIRD
            in 11..17 -> DAYTIME_CRUISER
            in 18..22 -> EVENING_DRIFTER
            else -> NIGHT_OWL
        }
    }
}

data class Recap(
    val range: DateRange,
    val summary: ListeningSummary,
    val topArtists: List<ArtistPlayCount>,
    val topTracks: List<TrackPlayCount>,
    val topAlbum: AlbumPlayCount?,
    val clock: ListeningClock,
    val personality: ListeningPersonality?,
    val longestStreak: Int,
    val newArtists: Int,
) {
    val isEmpty: Boolean get() = summary.streams == 0
}
