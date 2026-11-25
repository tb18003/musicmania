/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it, vi } from 'vitest'
import { HistorySync } from '$lib/domain/sync'
import { success } from '$lib/model/result'
import { FakeSpotifyRemote, InMemoryAppState, InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { HomeViewModel } from './HomeViewModel.svelte'

// Wednesday 25 November 2026, 18:00 local time.
const NOW = new Date(2026, 10, 25, 18, 0).getTime()
const at = (day: number, hour: number) => new Date(2026, 10, day, hour).getTime()

async function setup() {
  const history = new InMemoryHistoryRepository()
  const remote = new FakeSpotifyRemote()
  const sync = new HistorySync({ remote, history, appState: new InMemoryAppState(), now: () => NOW })
  const runSync = vi.fn(async () => null)
  const vm = new HomeViewModel({ history, remote, sync, runSync, now: () => NOW })
  return { vm, history, remote, sync, runSync }
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0))

describe('HomeViewModel', () => {
  it('summarises today, this week (from Monday) and the streak from local history', async () => {
    const { vm, history } = await setup()
    await history.addPlays([
      play({ playedAt: at(25, 9), msPlayed: 120_000 }), // today
      play({ playedAt: at(25, 10), msPlayed: 20_000 }), // today, too short for a stream
      play({ playedAt: at(24, 9), artistName: 'Nova Lights', msPlayed: 60_000 }), // Tuesday
      play({ playedAt: at(23, 9), artistName: 'Nova Lights', msPlayed: 60_000 }), // Monday
      play({ playedAt: at(22, 9), msPlayed: 600_000 }), // Sunday: last week
    ])

    await vm.load()

    expect(vm.state.today).toMatchObject({ listenedMs: 140_000, streams: 1 })
    expect(vm.state.week).toMatchObject({ listenedMs: 260_000, streams: 3 })
    expect(vm.state.topArtist?.artistName).toBe('Nova Lights')
    expect(vm.state.streaks).toMatchObject({ current: 4, longest: 4 })
    expect(vm.state.totalPlays).toBe(5)
    expect(vm.state.loading).toBe(false)
  })

  it('shows the profile and what is playing, and recomputes after each sync', async () => {
    const { vm, remote, sync } = await setup()
    remote.playing = success({
      trackName: 'Low Tide',
      artistName: 'Coral Echo',
      imageUrl: null,
      isPlaying: true,
      progressMs: 1,
      durationMs: 2,
    })
    remote.pages.push(success({ plays: [play({ playedAt: at(25, 12) })], nextAfter: null }))

    const stop = vm.start()
    await settle()
    expect(vm.state.profile?.displayName).toBe('Ana')
    expect(vm.state.nowPlaying?.trackName).toBe('Low Tide')
    expect(vm.state.totalPlays).toBe(0)

    await sync.run()
    await settle()
    expect(vm.state.totalPlays).toBe(1)
    expect(vm.state.sync.lastSuccess).toBe(NOW)
    stop()
  })

  it('"sync now" forces a sync and surfaces its error', async () => {
    const { vm, runSync } = await setup()
    runSync.mockResolvedValueOnce({ kind: 'network' } as never)
    await vm.syncNow()
    expect(runSync).toHaveBeenCalledWith(true)
    expect(vm.state.error).toEqual({ kind: 'network' })
  })
})
