/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { buildPages, buildRecap, isEmptyRecap, recapRange } from './recap'

// Wednesday 25 November 2026, 12:00 local.
const NOW = new Date(2026, 10, 25, 12).getTime()

describe('recap ranges (RecapViewModel.rangeFor)', () => {
  it('rolling 30 days, calendar months and years in local time', () => {
    const last30 = recapRange('LAST_30_DAYS', NOW)
    expect(last30.end - last30.start).toBe(30 * 24 * 3_600_000 + 1_000)
    expect(recapRange('THIS_MONTH', NOW)).toEqual({
      start: new Date(2026, 10, 1).getTime(),
      end: new Date(2026, 11, 1).getTime(),
    })
    expect(recapRange('LAST_MONTH', new Date(2026, 0, 15).getTime())).toEqual({
      start: new Date(2025, 11, 1).getTime(),
      end: new Date(2026, 0, 1).getTime(),
    })
    expect(recapRange('LAST_YEAR', NOW)).toEqual({
      start: new Date(2025, 0, 1).getTime(),
      end: new Date(2026, 0, 1).getTime(),
    })
  })
})

describe('buildRecap (BuildRecapUseCaseTest.kt)', () => {
  it('recap aggregates tops, personality and new artists', async () => {
    const history = new InMemoryHistoryRepository()
    const night = new Date(2026, 10, 25, 2).getTime() // 02:00 local
    await history.addPlays([
      play({ trackName: 'A', artistName: 'Old', playedAt: NOW - 400 * 24 * 3_600_000 }),
      play({ trackName: 'B', artistName: 'New', playedAt: night }),
      play({ trackName: 'B', artistName: 'New', playedAt: night + 1_000 }),
      play({ trackName: 'C', artistName: 'Old', playedAt: night + 2_000 }),
    ])
    const range = { start: NOW - 7 * 24 * 3_600_000, end: NOW + 1_000 }

    const recap = await buildRecap(history, range, NOW)

    expect(recap.summary.streams).toBe(3)
    expect(recap.topArtists[0]?.item.name).toBe('New')
    expect(recap.topTracks[0]?.item.name).toBe('B')
    expect(recap.personality).toBe('NIGHT_OWL')
    expect(recap.newArtists).toBe(1)
    expect(recap.longestStreak).toBe(1)
  })

  it('pages are built from what the recap has', async () => {
    const empty = await buildRecap(new InMemoryHistoryRepository(), recapRange('THIS_YEAR', NOW), NOW)
    expect(isEmptyRecap(empty)).toBe(true)
    expect(buildPages(empty)).toEqual(['INTRO', 'MINUTES', 'DISCOVERIES', 'SHARE'])

    const history = new InMemoryHistoryRepository()
    await history.addPlays([play({ playedAt: NOW - 3_600_000 })])
    const full = await buildRecap(history, recapRange('THIS_YEAR', NOW), NOW)
    expect(buildPages(full)).toEqual([
      'INTRO',
      'MINUTES',
      'TOP_ARTIST',
      'TOP_TRACKS',
      'PERSONALITY',
      'DISCOVERIES',
      'SHARE',
    ])
  })
})
