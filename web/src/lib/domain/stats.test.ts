/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { addDays } from './dates'
import { play } from '$testing/fixtures'
import { computeStreaks, dedupeKeyOf, summarize, topArtist } from './stats'

describe('dedupeKeyOf (parity with PlayEventEntity.dedupeKeyOf)', () => {
  it('ignores case, surrounding spaces and sub-second differences', () => {
    expect(dedupeKeyOf(' Low Tide ', 'CORAL Echo', 1_700_000_000_999)).toBe('low tide|coral echo|1700000000')
    expect(dedupeKeyOf('Low Tide', 'Coral Echo', 1_700_000_000_000)).toBe(
      dedupeKeyOf('low tide', 'coral echo', 1_700_000_000_500),
    )
  })

  it('a different second is a different play', () => {
    expect(dedupeKeyOf('a', 'b', 1_000)).not.toBe(dedupeKeyOf('a', 'b', 2_000))
  })
})

describe('summarize (parity with PlayEventDao.observeSummary)', () => {
  it('sums time, counts streams from 30 s, and counts distinct tracks and artists by name', () => {
    const summary = summarize([
      play({ msPlayed: 200_000 }),
      play({ msPlayed: 29_999 }), // too short to be a stream, still listening time
      play({ trackName: 'High Tide', msPlayed: 30_000 }),
      play({ artistName: 'Nova Lights', trackName: 'Low Tide', msPlayed: 60_000 }),
    ])
    expect(summary).toEqual({ listenedMs: 319_999, streams: 3, uniqueTracks: 3, uniqueArtists: 2 })
  })

  it('no plays, all zero', () => {
    expect(summarize([])).toEqual({ listenedMs: 0, streams: 0, uniqueTracks: 0, uniqueArtists: 0 })
  })
})

describe('computeStreaks (same cases as StatsCalculatorsTest.kt)', () => {
  const today = '2026-06-15'
  const daysBefore = (from: number, to: number) =>
    Array.from({ length: to - from + 1 }, (_, i) => addDays(today, -(from + i)))

  it('empty history has no streak', () => {
    expect(computeStreaks([], today)).toEqual({ current: 0, longest: 0, longestEndedOn: null })
  })

  it('current streak survives until the end of the next day', () => {
    const streaks = computeStreaks(daysBefore(1, 5), today)
    expect(streaks.current).toBe(5)
    expect(streaks.longest).toBe(5)
  })

  it('gap breaks the current streak but keeps the longest', () => {
    const recent = [today, addDays(today, -1)]
    const streaks = computeStreaks([...daysBefore(10, 19), ...recent, ...recent], today)
    expect(streaks.current).toBe(2)
    expect(streaks.longest).toBe(10)
    expect(streaks.longestEndedOn).toBe(addDays(today, -10))
  })

  it('stale streak is not current', () => {
    const streaks = computeStreaks([addDays(today, -3)], today)
    expect(streaks.current).toBe(0)
    expect(streaks.longest).toBe(1)
  })

  it('streaks cross month boundaries', () => {
    expect(computeStreaks(['2026-02-27', '2026-02-28', '2026-03-01'], '2026-03-01').current).toBe(3)
  })
})

describe('topArtist', () => {
  it('ranks by streams, then listening time; short plays do not count', () => {
    const result = topArtist([
      play({ artistName: 'A', msPlayed: 40_000 }),
      play({ artistName: 'A', msPlayed: 40_000 }),
      play({ artistName: 'B', msPlayed: 200_000 }),
      play({ artistName: 'B', msPlayed: 200_000 }),
      play({ artistName: 'C', msPlayed: 10_000 }),
      play({ artistName: 'C', msPlayed: 10_000 }),
      play({ artistName: 'C', msPlayed: 10_000 }),
    ])
    expect(result?.artistName).toBe('B')
    expect(result?.streams).toBe(2)
  })

  it('nothing streamed, no top artist', () => {
    expect(topArtist([play({ msPlayed: 1_000 })])).toBeNull()
  })
})
