/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import {
  computeMilestones,
  GetInsights,
  listenedChange,
  NewArtistsAccumulator,
  nextGoals,
  PatternAccumulator,
  peakHour,
  personalityFromPeakHour,
  previousWindow,
  windowFor,
} from './insights'
import { feed } from './stats'

// Wednesday 25 November 2026, 18:00 local.
const NOW = new Date(2026, 10, 25, 18).getTime()
const at = (day: number, hour: number) => new Date(2026, 10, day, hour, 30).getTime()

describe('windows (TimeRangesTest.kt)', () => {
  it('ranges map to a window ending now; lifetime starts at the epoch', () => {
    const window = windowFor('FOUR_WEEKS', NOW)
    expect(window.end).toBeGreaterThan(NOW)
    expect(window.end - window.start).toBe(28 * 24 * 60 * 60 * 1000)
    expect(windowFor('LIFETIME', NOW).start).toBe(0)
  })

  it('the previous window has the same length and ends where this one starts', () => {
    expect(previousWindow({ start: 100, end: 160 })).toEqual({ start: 40, end: 100 })
  })
})

describe('clock and personality', () => {
  it('personality follows peak hour', () => {
    expect(personalityFromPeakHour(2)).toBe('NIGHT_OWL')
    expect(personalityFromPeakHour(7)).toBe('EARLY_BIRD')
    expect(personalityFromPeakHour(20)).toBe('EVENING_DRIFTER')
    expect(personalityFromPeakHour(13)).toBe('DAYTIME_CRUISER')
    expect(personalityFromPeakHour(null)).toBeNull()
  })

  it('the peak hour is the busiest one; an empty clock has none', () => {
    const clock = Array.from({ length: 24 }, (_, h) => (h === 21 ? 500 : h === 9 ? 100 : 0))
    expect(peakHour(clock)).toBe(21)
    expect(peakHour(Array.from({ length: 24 }, () => 0))).toBeNull()
  })

  it('buckets by local hour and Monday-first weekday', () => {
    const patterns = new PatternAccumulator()
    feed(
      [
        play({ playedAt: at(23, 21), msPlayed: 1_000 }), // Monday 21:30
        play({ playedAt: at(23, 21), msPlayed: 2_000, trackName: 'Other' }),
        play({ playedAt: at(29, 8), msPlayed: 500 }), // Sunday 08:30
      ],
      patterns,
    )
    const { clock, heatmap } = patterns.result()
    expect(clock[21]).toBe(3_000)
    expect(clock[8]).toBe(500)
    expect(heatmap[0]?.[21]).toBe(3_000)
    expect(heatmap[6]?.[8]).toBe(500)
  })
})

describe('habits (observeHabits / observePlatforms)', () => {
  it('rates only count plays that report them; platforms by listening time', () => {
    const patterns = new PatternAccumulator()
    feed(
      [
        play({ skipped: true, shuffle: true, platform: 'iOS', msPlayed: 100 }),
        play({ skipped: false, shuffle: false, platform: 'Android', msPlayed: 300 }),
        play({ skipped: false, shuffle: null, platform: 'iOS', msPlayed: 50 }),
        play({ skipped: null, shuffle: null, platform: null }), // synced play: no data
      ],
      patterns,
    )
    const { habits } = patterns.result()
    expect(habits.skipRate).toBeCloseTo(1 / 3)
    expect(habits.shuffleRate).toBe(0.5)
    expect(habits.platforms).toEqual([
      { platform: 'Android', listenedMs: 300 },
      { platform: 'iOS', listenedMs: 150 },
    ])
  })

  it('sources that never report skips give no rate at all', () => {
    const patterns = new PatternAccumulator()
    feed([play(), play({ playedAt: 2 })], patterns)
    expect(patterns.result().habits).toMatchObject({ skipRate: null, shuffleRate: null, platforms: [] })
  })
})

describe('comparison and milestones (MilestoneCalculatorTest.kt)', () => {
  it('relative change of listening time, null without previous data', () => {
    const summary = (listenedMs: number) => ({ listenedMs, streams: 0, uniqueTracks: 0, uniqueArtists: 0 })
    expect(listenedChange({ current: summary(150), previous: summary(100) })).toBe(0.5)
    expect(listenedChange({ current: summary(150), previous: summary(0) })).toBeNull()
  })

  it('milestones reflect lifetime totals', () => {
    const summary = { listenedMs: 12_000 * 60_000, streams: 900, uniqueTracks: 10, uniqueArtists: 60 }
    const milestones = computeMilestones(summary, 8)

    const minutes = milestones.filter((m) => m.type === 'MINUTES')
    expect(minutes.find((m) => m.threshold === 10_000)?.achieved).toBe(true)
    expect(minutes.find((m) => m.threshold === 50_000)?.achieved).toBe(false)

    const next = Object.fromEntries(nextGoals(milestones).map((m) => [m.type, m]))
    expect(next.MINUTES?.threshold).toBe(50_000)
    expect(next.STREAMS?.threshold).toBe(1_000)
    expect(next.STREAMS?.progress).toBe(0.9)
    expect(next.STREAK_DAYS?.threshold).toBe(30)
  })
})

describe('new artists (observeNewArtists)', () => {
  it('counts artists first heard inside the window', () => {
    const window = { start: 1_000, end: 2_000 }
    const accumulator = new NewArtistsAccumulator(window)
    feed(
      [
        play({ artistName: 'Old', playedAt: 500 }),
        play({ artistName: 'Old', playedAt: 1_500 }),
        play({ artistName: 'New', playedAt: 1_200 }),
        play({ artistName: 'Later', playedAt: 2_500 }),
      ],
      accumulator,
    )
    expect(accumulator.result()).toBe(1)
  })
})

describe('GetInsights', () => {
  it('window totals versus the previous window, plus lifetime milestones and streaks', async () => {
    const history = new InMemoryHistoryRepository()
    await history.addPlays([
      play({ playedAt: at(25, 16), msPlayed: 300_000 }), // today, before "now" (18:00)
      play({ playedAt: at(24, 21), msPlayed: 600_000, trackName: 'B' }),
      play({ playedAt: new Date(2026, 9, 20, 9).getTime(), msPlayed: 300_000 }), // previous 4 weeks
      play({ playedAt: new Date(2020, 0, 1, 9).getTime(), msPlayed: 60_000 }), // long ago
    ])

    const insights = await new GetInsights({ history, now: () => NOW }).load('FOUR_WEEKS')

    expect(insights.comparison.current.listenedMs).toBe(900_000)
    expect(insights.comparison.previous.listenedMs).toBe(300_000)
    expect(insights.personality).toBe('EVENING_DRIFTER')
    expect(insights.streaks).toMatchObject({ current: 2, longest: 2 })
    expect(insights.milestones.find((m) => m.type === 'STREAMS')?.value).toBe(4)
    expect(insights.hasHistory).toBe(true)
  })

  it('an empty history has no personality and says so', async () => {
    const insights = await new GetInsights({ history: new InMemoryHistoryRepository(), now: () => NOW }).load(
      'LIFETIME',
    )
    expect(insights.personality).toBeNull()
    expect(insights.hasHistory).toBe(false)
  })
})
