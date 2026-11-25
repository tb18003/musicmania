/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { GetInsights } from '$lib/domain/insights'
import { buildSharedStats } from '$lib/domain/leaderboard'
import { GetTopItems } from '$lib/domain/top'
import type { PlayEvent } from '$lib/model/music'
import { FakeSpotifyRemote } from '$testing/fakes'
import { testDatabase } from '../testing'
import { DexieHistoryRepository } from './repositories'
import { CachedTopItemsRepository } from './top-items'

// Several scan pages; fake-indexeddb is pure JavaScript, so the full 300k of a real export stays out of CI.
const PLAYS = 30_000
const NOW = Date.UTC(2026, 10, 25, 18)

/** Months of imported history: plays every 10 minutes, 500 artists, 5,000 tracks. */
function history(): PlayEvent[] {
  return Array.from({ length: PLAYS }, (_, i) => ({
    trackId: null,
    trackName: `Track ${i % 5_000}`,
    artistName: `Artist ${i % 500}`,
    albumName: `Album ${i % 1_000}`,
    imageUrl: null,
    playedAt: NOW - (PLAYS - i) * 10 * 60_000,
    msPlayed: 180_000,
    source: 'IMPORT',
    skipped: i % 7 === 0,
    shuffle: i % 3 === 0,
    platform: i % 2 === 0 ? 'iOS' : 'Android',
  }))
}

describe('streamed statistics over a large history', () => {
  it('scan visits every play in order without a giant array, and every screen copes', async () => {
    const repository = new DexieHistoryRepository(testDatabase())
    const plays = history()
    for (let i = 0; i < plays.length; i += 5_000) await repository.addPlays(plays.slice(i, i + 5_000))

    let visited = 0
    let last = -Infinity
    let ordered = true
    await repository.scan(0, NOW + 1, (play) => {
      visited++
      if (play.playedAt < last) ordered = false
      last = play.playedAt
    })
    expect(visited).toBe(PLAYS)
    expect(ordered).toBe(true)

    const now = () => NOW
    const top = new GetTopItems({
      top: new CachedTopItemsRepository(testDatabase(), new FakeSpotifyRemote(), now),
      history: repository,
      now,
    })
    const lifetime = await top.artists('LIFETIME')
    expect(lifetime.items).toHaveLength(50)
    expect(lifetime.items[0]?.streams).toBe(PLAYS / 500)

    const insights = await new GetInsights({ history: repository, now }).load('LIFETIME')
    expect(insights.comparison.current.streams).toBe(PLAYS)
    expect(insights.habits.platforms.map((p) => p.platform).sort()).toEqual(['Android', 'iOS'])

    const shared = await buildSharedStats(repository, NOW)
    expect(shared.find((s) => s.period === 'all_time')?.streams).toBe(PLAYS)
  }, 60_000)
})
