/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { ListeningSummary, PlayEvent, Streaks } from '$lib/model/music'
import type { TimeRange } from '$lib/model/top'
import { localDateOf, type DateRange } from './dates'
import type { HistoryRepository } from './repositories'
import { computeStreaks, SummaryAccumulator, type Accumulator } from './stats'

const DAY_MS = 24 * 60 * 60_000
const HOURS = 24

/** Rolling windows ending now, like TimeRange.toDateRange; lifetime starts at the epoch. */
const RANGE_DAYS: Record<TimeRange, number | null> = { FOUR_WEEKS: 28, SIX_MONTHS: 182, ONE_YEAR: 365, LIFETIME: null }

export function windowFor(range: TimeRange, now: number): DateRange {
  // Small margin so plays recorded "right now" are inside the half-open range.
  const end = now + 60_000
  const days = RANGE_DAYS[range]
  return { start: days === null ? 0 : end - days * DAY_MS, end }
}

/** The window of identical length immediately preceding this one (DateRange.previous). */
export const previousWindow = ({ start, end }: DateRange): DateRange => ({ start: start - (end - start), end: start })

// ---- listening clock and personality ---------------------------------------------------------------

/** Milliseconds listened per local hour of the day (ListeningClock). */
export type ListeningClock = readonly number[]

export function peakHour(clock: ListeningClock): number | null {
  let peak: number | null = null
  clock.forEach((ms, hour) => {
    if (ms > 0 && (peak === null || ms > (clock[peak] ?? 0))) peak = hour
  })
  return peak
}

export type Personality = 'EARLY_BIRD' | 'DAYTIME_CRUISER' | 'EVENING_DRIFTER' | 'NIGHT_OWL'

/** ListeningPersonality.fromPeakHour. */
export function personalityFromPeakHour(hour: number | null): Personality | null {
  if (hour === null) return null
  if (hour >= 5 && hour <= 10) return 'EARLY_BIRD'
  if (hour >= 11 && hour <= 17) return 'DAYTIME_CRUISER'
  if (hour >= 18 && hour <= 22) return 'EVENING_DRIFTER'
  return 'NIGHT_OWL'
}

// ---- habits ---------------------------------------------------------------------------------------

export interface PlaybackHabits {
  /** 0..1, over the plays that report it; null when the source never does (synced plays). */
  readonly skipRate: number | null
  readonly shuffleRate: number | null
  /** Listening time per platform, most used first. */
  readonly platforms: readonly { platform: string; listenedMs: number }[]
}

// ---- comparison and milestones ----------------------------------------------------------------------

export interface PeriodComparison {
  readonly current: ListeningSummary
  readonly previous: ListeningSummary
}

/** Relative change of listening time, null when there is no previous data. */
export const listenedChange = ({ current, previous }: PeriodComparison): number | null =>
  previous.listenedMs === 0 ? null : (current.listenedMs - previous.listenedMs) / previous.listenedMs

export type MilestoneType = 'MINUTES' | 'STREAMS' | 'ARTISTS' | 'STREAK_DAYS'

export interface Milestone {
  readonly type: MilestoneType
  readonly threshold: number
  readonly value: number
  readonly achieved: boolean
  /** 0..1 towards the threshold. */
  readonly progress: number
}

/** MilestoneCalculator.thresholds. */
export const MILESTONE_THRESHOLDS: Record<MilestoneType, readonly number[]> = {
  MINUTES: [1_000, 10_000, 50_000, 100_000, 250_000],
  STREAMS: [1_000, 5_000, 25_000, 100_000],
  ARTISTS: [50, 250, 1_000, 2_500],
  STREAK_DAYS: [7, 30, 100, 365],
}

export function computeMilestones(lifetime: ListeningSummary, longestStreak: number): Milestone[] {
  const values: Record<MilestoneType, number> = {
    MINUTES: Math.floor(lifetime.listenedMs / 60_000),
    STREAMS: lifetime.streams,
    ARTISTS: lifetime.uniqueArtists,
    STREAK_DAYS: longestStreak,
  }
  return (Object.keys(MILESTONE_THRESHOLDS) as MilestoneType[]).flatMap((type) =>
    MILESTONE_THRESHOLDS[type].map((threshold) => ({
      type,
      threshold,
      value: values[type],
      achieved: values[type] >= threshold,
      progress: Math.min(1, Math.max(0, values[type] / threshold)),
    })),
  )
}

/** The closest milestone not yet achieved, per type ("next goal"). */
export const nextGoals = (milestones: readonly Milestone[]): Milestone[] =>
  (Object.keys(MILESTONE_THRESHOLDS) as MilestoneType[]).flatMap((type) => {
    const next = milestones.find((m) => m.type === type && !m.achieved)
    return next ? [next] : []
  })

// ---- the accumulator ---------------------------------------------------------------------------------

export interface PatternResult {
  readonly clock: ListeningClock
  /** [weekday][hour] in ms, weekday 0 = Monday. */
  readonly heatmap: readonly (readonly number[])[]
  readonly habits: PlaybackHabits
}

/** Hour of day, weekday × hour, skip/shuffle and platforms, in local time, one play at a time. */
export class PatternAccumulator implements Accumulator<PatternResult> {
  readonly #clock = Array.from({ length: HOURS }, () => 0)
  readonly #heatmap = Array.from({ length: 7 }, () => Array.from({ length: HOURS }, () => 0))
  #skipKnown = 0
  #skipped = 0
  #shuffleKnown = 0
  #shuffled = 0
  readonly #platforms = new Map<string, number>()

  add(play: PlayEvent) {
    const date = new Date(play.playedAt)
    const hour = date.getHours()
    const weekday = (date.getDay() + 6) % 7
    this.#clock[hour] = (this.#clock[hour] ?? 0) + play.msPlayed
    const row = this.#heatmap[weekday]
    if (row) row[hour] = (row[hour] ?? 0) + play.msPlayed
    if (play.skipped !== null) {
      this.#skipKnown++
      if (play.skipped) this.#skipped++
    }
    if (play.shuffle !== null) {
      this.#shuffleKnown++
      if (play.shuffle) this.#shuffled++
    }
    if (play.platform) this.#platforms.set(play.platform, (this.#platforms.get(play.platform) ?? 0) + play.msPlayed)
  }

  result(): PatternResult {
    return {
      clock: [...this.#clock],
      heatmap: this.#heatmap.map((row) => [...row]),
      habits: {
        skipRate: this.#skipKnown === 0 ? null : this.#skipped / this.#skipKnown,
        shuffleRate: this.#shuffleKnown === 0 ? null : this.#shuffled / this.#shuffleKnown,
        platforms: [...this.#platforms.entries()]
          .map(([platform, listenedMs]) => ({ platform, listenedMs }))
          .sort((a, b) => b.listenedMs - a.listenedMs),
      },
    }
  }
}

/** Artists whose very first play falls inside [start, end) (observeNewArtists). Needs every play. */
export class NewArtistsAccumulator implements Accumulator<number> {
  readonly #firstPlay = new Map<string, number>()

  constructor(private readonly window: DateRange) {}

  add(play: PlayEvent) {
    const first = this.#firstPlay.get(play.artistName)
    if (first === undefined || play.playedAt < first) this.#firstPlay.set(play.artistName, play.playedAt)
  }

  result(): number {
    let count = 0
    for (const first of this.#firstPlay.values()) if (first >= this.window.start && first < this.window.end) count++
    return count
  }
}

// ---- use case ------------------------------------------------------------------------------------------

export interface Insights extends PatternResult {
  readonly personality: Personality | null
  readonly streaks: Streaks
  readonly milestones: readonly Milestone[]
  readonly comparison: PeriodComparison
  readonly hasHistory: boolean
}

/** Port of GetInsightsUseCase, in at most two streamed passes over the history. */
export class GetInsights {
  constructor(private readonly deps: { history: HistoryRepository; now: () => number }) {}

  async load(range: TimeRange): Promise<Insights> {
    const { history, now } = this.deps
    const window = windowFor(range, now())
    const previous = previousWindow(window)
    const patterns = new PatternAccumulator()
    const current = new SummaryAccumulator()
    const before = new SummaryAccumulator()
    const lifetime = new SummaryAccumulator()

    // One pass over everything up to now feeds the window, the previous window and the lifetime.
    await history.scan(0, window.end, (play) => {
      lifetime.add(play)
      if (play.playedAt >= window.start) {
        current.add(play)
        patterns.add(play)
      } else if (play.playedAt >= previous.start) {
        before.add(play)
      }
    })
    const times = await history.playTimes()
    const streaks = computeStreaks(times.map(localDateOf), localDateOf(now()))
    const result = patterns.result()
    const lifetimeSummary = lifetime.result()
    return {
      ...result,
      personality: personalityFromPeakHour(peakHour(result.clock)),
      streaks,
      milestones: computeMilestones(lifetimeSummary, streaks.longest),
      comparison: { current: current.result(), previous: before.result() },
      hasHistory: times.length > 0,
    }
  }
}
