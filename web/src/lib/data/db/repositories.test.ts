/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { play } from '$testing/fixtures'
import { DefaultSessionRepository } from '../session'
import { testDatabase } from '../testing'
import { DexieAppStateRepository, DexieHistoryRepository, SCAN_PAGE } from './repositories'

describe('DexieHistoryRepository', () => {
  it('ignores plays already stored, from sync or import (same dedupe key)', async () => {
    const history = new DexieHistoryRepository(testDatabase())
    const synced = play({ playedAt: 1_000_000, source: 'SYNC' })
    const importedSame = play({ playedAt: 1_000_400, trackName: 'LOW TIDE ', source: 'IMPORT' })

    expect(await history.addPlays([synced, play({ playedAt: 2_000_000 })])).toBe(2)
    expect(await history.addPlays([importedSame, play({ playedAt: 3_000_000 })])).toBe(1)
    expect(await history.addPlays([play({ playedAt: 4_000_000 }), play({ playedAt: 4_000_000 })])).toBe(1)
    expect(await history.count()).toBe(4)
  })

  it('queries half-open ranges and pages newest first', async () => {
    const history = new DexieHistoryRepository(testDatabase())
    await history.addPlays([1, 2, 3, 4, 5].map((i) => play({ playedAt: i * 1_000_000 })))

    expect((await history.playsBetween(2_000_000, 4_000_000)).map((p) => p.playedAt)).toEqual([2_000_000, 3_000_000])
    const first = await history.recent(2)
    expect(first.map((p) => p.playedAt)).toEqual([5_000_000, 4_000_000])
    const next = await history.recent(2, first[1]?.playedAt)
    expect(next.map((p) => p.playedAt)).toEqual([3_000_000, 2_000_000])
    expect(await history.playTimes()).toEqual([1, 2, 3, 4, 5].map((i) => i * 1_000_000))
  })

  it('scan pages through the range in order, never repeating or skipping plays that share a timestamp', async () => {
    const history = new DexieHistoryRepository(testDatabase())
    // More plays at one instant than a page holds, around page boundaries.
    const same = Array.from({ length: SCAN_PAGE + 10 }, (_, i) => play({ playedAt: 5_000_000, trackName: `Same ${i}` }))
    const spread = Array.from({ length: SCAN_PAGE }, (_, i) =>
      play({ playedAt: 6_000_000 + i * 1_000, trackName: `S${i}` }),
    )
    await history.addPlays([...same, ...spread, play({ playedAt: 1_000, trackName: 'Before' })])

    const seen: string[] = []
    let ordered = true
    let last = -Infinity
    await history.scan(2_000, Number.MAX_SAFE_INTEGER, (p) => {
      seen.push(p.trackName)
      if (p.playedAt < last) ordered = false
      last = p.playedAt
    })

    expect(seen).toHaveLength(same.length + spread.length)
    expect(new Set(seen).size).toBe(seen.length)
    expect(seen).not.toContain('Before')
    expect(ordered).toBe(true)
  }, 60_000)

  it('returns plain plays, without storage fields', async () => {
    const history = new DexieHistoryRepository(testDatabase())
    await history.addPlays([play()])
    expect(Object.keys((await history.recent(1))[0] ?? {})).not.toContain('dedupeKey')
  })
})

describe('DexieAppStateRepository', () => {
  it('remembers the sync cursor and last sync time', async () => {
    const state = new DexieAppStateRepository(testDatabase())
    expect(await state.recentlyPlayedCursor()).toBeNull()
    await state.setRecentlyPlayedCursor(42)
    await state.setLastSync(7)
    expect(await state.recentlyPlayedCursor()).toBe(42)
    expect(await state.lastSync()).toBe(7)
  })
})

describe('sign out', () => {
  it('wipes every table: plays, sync state and secrets', async () => {
    const db = testDatabase()
    await new DexieHistoryRepository(db).addPlays([play()])
    await db.secrets.put({ key: 'auth.refreshToken', value: 'sealed' })
    let forgotten = false

    await new DefaultSessionRepository(db, () => (forgotten = true)).signOut()

    expect(forgotten).toBe(true)
    const names = await indexedDB.databases()
    expect(names.map((entry) => entry.name)).not.toContain(db.name)
  })
})
