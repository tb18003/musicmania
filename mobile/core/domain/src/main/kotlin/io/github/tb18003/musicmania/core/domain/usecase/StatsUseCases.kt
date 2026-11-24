/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.stats.MilestoneCalculator
import io.github.tb18003.musicmania.core.domain.stats.StreakCalculator
import io.github.tb18003.musicmania.core.domain.stats.toDateRange
import io.github.tb18003.musicmania.core.model.DailyListening
import io.github.tb18003.musicmania.core.model.DateRange
import io.github.tb18003.musicmania.core.model.ListeningClock
import io.github.tb18003.musicmania.core.model.ListeningPersonality
import io.github.tb18003.musicmania.core.model.Milestone
import io.github.tb18003.musicmania.core.model.PeriodComparison
import io.github.tb18003.musicmania.core.model.PlaybackHabits
import io.github.tb18003.musicmania.core.model.Recap
import io.github.tb18003.musicmania.core.model.Streaks
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.model.WeekdayHourCell
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ListeningOverview(
    val comparison: PeriodComparison,
    val daily: List<DailyListening>,
    val hasHistory: Boolean,
)

/** Totals for a period, compared with the immediately preceding period of equal length. */
class GetListeningOverviewUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(range: TimeRange): Flow<ListeningOverview> {
        val window = range.toDateRange(clock)
        return combine(
            history.observeSummary(window),
            history.observeSummary(window.previous()),
            history.observeDaily(window),
            history.observeEventCount(),
        ) { current, previous, daily, count ->
            ListeningOverview(PeriodComparison(current, previous), daily, hasHistory = count > 0)
        }
    }
}

data class Insights(
    val clock: ListeningClock,
    val personality: ListeningPersonality?,
    val heatmap: List<WeekdayHourCell>,
    val habits: PlaybackHabits,
    val streaks: Streaks,
    val milestones: List<Milestone>,
    val comparison: PeriodComparison,
)

class GetInsightsUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(range: TimeRange): Flow<Insights> {
        val window = range.toDateRange(clock)
        val lifetime = TimeRange.LIFETIME.toDateRange(clock)
        val listeningClock = combine(history.observeClock(window), history.observeWeekdayHour(window)) { c, h -> c to h }
        val totals = combine(history.observeSummary(window), history.observeSummary(window.previous())) { cur, prev ->
            PeriodComparison(cur, prev)
        }
        return combine(
            listeningClock,
            history.observeHabits(window),
            history.observeDaily(lifetime),
            history.observeSummary(lifetime),
            totals,
        ) { (clockData, heatmap), habits, lifetimeDaily, lifetimeSummary, comparison ->
            val streaks = StreakCalculator.compute(lifetimeDaily.map { it.date }, LocalDate.now(clock))
            Insights(
                clock = clockData,
                personality = ListeningPersonality.fromPeakHour(clockData.peakHour),
                heatmap = heatmap,
                habits = habits,
                streaks = streaks,
                milestones = MilestoneCalculator.compute(lifetimeSummary, streaks.longest),
                comparison = comparison,
            )
        }
    }
}

/** "Wrapped, any time": everything a story-style recap needs for an arbitrary window. */
class BuildRecapUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(range: DateRange): Flow<Recap> {
        val tops = combine(
            history.observeTopArtists(range, RECAP_TOP),
            history.observeTopTracks(range, RECAP_TOP),
            history.observeTopAlbums(range, 1),
        ) { artists, tracks, albums -> Triple(artists, tracks, albums.firstOrNull()) }
        return combine(
            history.observeSummary(range),
            tops,
            history.observeClock(range),
            history.observeDaily(range),
            history.observeNewArtists(range),
        ) { summary, (artists, tracks, album), listeningClock, daily, newArtists ->
            val today = LocalDate.now(clock)
            Recap(
                range = range,
                summary = summary,
                topArtists = artists,
                topTracks = tracks,
                topAlbum = album,
                clock = listeningClock,
                personality = ListeningPersonality.fromPeakHour(listeningClock.peakHour),
                longestStreak = StreakCalculator.compute(daily.map { it.date }, today).longest,
                newArtists = newArtists,
            )
        }
    }

    private companion object {
        const val RECAP_TOP = 5
    }
}
