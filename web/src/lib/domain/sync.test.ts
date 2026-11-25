/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import type { RecentlyPlayedPage } from '$lib/model/music'
import { failure, success, type DataResult } from '$lib/model/result'
import { FakeSpotifyRemote, InMemoryAppState, InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { HistorySync, MIN_SYNC_INTERVAL_MS } from './sync'

function setup(pages: DataResult<RecentlyPlayedPage>[]) {
  const remote = new FakeSpotifyRemote()
  remote.pages.push(...pages)
  let now = 1_000_000_000_000
  const history = new InMemoryHistoryRepository()
  const state = new InMemoryAppState()
  const sync = new HistorySync({ remote, history, appState: state, now: () => now })
  return { sync, history, state, calls: remote.calls, advance: (ms: number) => (now += ms) }
}

const page = (count: number, nextAfter: number | null = null) =>
  success({ plays: Array.from({ length: count }, (_, i) => play({ playedAt: 1_000_000 + i * 1_000 })), nextAfter })

describe('HistorySync', () => {
  it('stores new plays and continues from Spotify cursor next time', async () => {
    const { sync, history, state, calls, advance } = setup([page(3, 5_000), page(0)])

    const first = await sync.run()
    expect(first).toEqual(success({ skipped: false, inserted: 3, possibleGap: false }))
    expect(history.plays).toHaveLength(3)
    expect(state.cursor).toBe(5_000)

    advance(MIN_SYNC_INTERVAL_MS)
    await sync.run()
    expect(calls).toEqual([null, 5_000])
  })

  it('without a cursor from Spotify, continues after the newest stored play', async () => {
    const { sync, state } = setup([page(2)])
    await sync.run()
    expect(state.cursor).toBe(1_001_000)
  })

  it('is throttled to spare the shared Spotify quota, unless forced', async () => {
    const { sync, calls, advance } = setup([page(1), page(1), page(1)])
    await sync.run()
    advance(MIN_SYNC_INTERVAL_MS - 1)
    expect(await sync.run()).toMatchObject({ ok: true, data: { skipped: true } })
    await sync.run({ force: true })
    expect(calls).toHaveLength(2)
  })

  it('concurrent runs share one request', async () => {
    const { sync, calls } = setup([page(1)])
    await Promise.all([sync.run(), sync.run(), sync.run()])
    expect(calls).toHaveLength(1)
  })

  it('a full page after a known cursor warns that older plays may be missing', async () => {
    const { sync, advance } = setup([page(10, 2_000), page(50, 3_000)])
    await sync.run()
    expect(sync.status.possibleGap).toBe(false)
    advance(MIN_SYNC_INTERVAL_MS)
    expect(await sync.run()).toMatchObject({ ok: true, data: { possibleGap: true } })
    expect(sync.status.possibleGap).toBe(true)
  })

  it('errors are returned typed and nothing is marked as synced', async () => {
    const { sync, state } = setup([failure({ kind: 'network' })])
    expect(await sync.run()).toEqual(failure({ kind: 'network' }))
    expect(state.last).toBeNull()
    expect(sync.status.running).toBe(false)
  })

  it('notifies subscribers while running and after success', async () => {
    const { sync } = setup([page(1)])
    const seen: boolean[] = []
    sync.subscribe((status) => seen.push(status.running))
    await sync.run()
    expect(seen).toEqual([false, true, false])
    expect(sync.status.lastSuccess).not.toBeNull()
  })
})
