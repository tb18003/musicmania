/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { HistoryViewModel, PAGE_SIZE } from './HistoryViewModel.svelte'

describe('HistoryViewModel', () => {
  it('groups the newest plays by local day and pages back in time', async () => {
    const history = new InMemoryHistoryRepository()
    const day = (d: number, minute: number) => new Date(2026, 10, d, 12, minute).getTime()
    // 30 plays on the 25th and 30 on the 24th: more than one page.
    await history.addPlays([
      ...Array.from({ length: 30 }, (_, i) => play({ playedAt: day(25, i) })),
      ...Array.from({ length: 30 }, (_, i) => play({ playedAt: day(24, i) })),
    ])
    const vm = new HistoryViewModel(history)

    await vm.refresh()
    expect(vm.state.days.map((d) => [d.date, d.plays.length])).toEqual([
      ['2026-11-25', 30],
      ['2026-11-24', PAGE_SIZE - 30],
    ])
    expect(vm.state.end).toBe(false)
    expect(vm.state.days[0]?.plays[0]?.playedAt).toBe(day(25, 29))

    await vm.loadMore()
    expect(vm.state.days.map((d) => [d.date, d.plays.length])).toEqual([
      ['2026-11-25', 30],
      ['2026-11-24', 30],
    ])
    expect(vm.state.end).toBe(true)
  })

  it('an empty history is not loading and has ended', async () => {
    const vm = new HistoryViewModel(new InMemoryHistoryRepository())
    await vm.refresh()
    expect(vm.state).toEqual({ days: [], loading: false, end: true })
  })
})
