/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildPages, buildRecap, recapRange } from '$lib/domain/recap'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import { InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import RecapScreen from './RecapScreen.svelte'
import { RecapViewModel, type RecapState } from './RecapViewModel.svelte'

const NOW = new Date(2026, 10, 25, 12).getTime()

async function recapState(): Promise<RecapState> {
  const history = new InMemoryHistoryRepository()
  await history.addPlays([
    play({ trackName: 'Low Tide', artistName: 'Coral Echo', playedAt: new Date(2026, 10, 20, 22).getTime() }),
    play({ trackName: 'Night Drive', artistName: 'Nova Lights', playedAt: new Date(2026, 10, 21, 22).getTime() }),
  ])
  const recap = await buildRecap(history, recapRange('THIS_YEAR', NOW), NOW)
  return { period: 'THIS_YEAR', loading: false, recap, pages: buildPages(recap) }
}

const context = (reduceMotion = false) =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['en'], prefersReducedMotion: () => reduceMotion }))
const callbacks = () => ({ onperiod: vi.fn(), onclose: vi.fn(), onimport: vi.fn() })

afterEach(() => vi.useRealTimers())

describe('RecapViewModel', () => {
  it('builds this year by default and rebuilds for another period', async () => {
    const history = new InMemoryHistoryRepository()
    await history.addPlays([play({ playedAt: new Date(2025, 5, 1).getTime() })])
    const vm = new RecapViewModel({ history, now: () => NOW })
    await vm.load()
    expect(vm.state.recap?.summary.streams).toBe(0)
    await vm.select('LAST_YEAR')
    expect(vm.state.recap?.summary.streams).toBe(1)
    expect(vm.state.pages).toContain('TOP_ARTIST')
  })
})

describe('RecapScreen', () => {
  it('stories advance on their own every 5.5 s and can be paused', async () => {
    vi.useFakeTimers()
    render(RecapScreen, { props: { state: await recapState(), ...callbacks() }, context: context() })
    expect(screen.getByText('Your recap')).toBeTruthy()

    await vi.advanceTimersByTimeAsync(5_600)
    expect(screen.getByText('You listened for')).toBeTruthy()

    await fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    await vi.advanceTimersByTimeAsync(20_000)
    expect(screen.getByText('You listened for')).toBeTruthy()
  })

  it('with reduced motion nothing moves by itself; zones and arrow keys turn the pages', async () => {
    vi.useFakeTimers()
    const handlers = callbacks()
    render(RecapScreen, { props: { state: await recapState(), ...handlers }, context: context(true) })
    await vi.advanceTimersByTimeAsync(20_000)
    expect(screen.getByText('Your recap')).toBeTruthy()

    const next = screen.getByRole('button', { name: 'Next story' })
    await fireEvent.pointerDown(next)
    await fireEvent.pointerUp(next)
    expect(screen.getByText('You listened for')).toBeTruthy()
    await fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(screen.getByText('Your top artist')).toBeTruthy()
    await fireEvent.keyDown(window, { key: 'ArrowLeft' })
    expect(screen.getByText('You listened for')).toBeTruthy()
    expect(screen.getByRole('progressbar', { name: 'Story 2 of 7' })).toBeTruthy()
    await fireEvent.keyDown(window, { key: 'Escape' })
    expect(handlers.onclose).toHaveBeenCalledOnce()
  })

  it('an empty period explains how to get recaps for past years', async () => {
    const handlers = callbacks()
    const empty = await buildRecap(new InMemoryHistoryRepository(), recapRange('LAST_YEAR', NOW), NOW)
    render(RecapScreen, {
      props: { state: { period: 'LAST_YEAR', loading: false, recap: empty, pages: buildPages(empty) }, ...handlers },
      context: context(),
    })
    expect(screen.getByRole('heading', { name: 'No plays in this period' })).toBeTruthy()
    await fireEvent.click(screen.getByRole('button', { name: 'Import Spotify history' }))
    expect(handlers.onimport).toHaveBeenCalledOnce()
  })

  it('the period picker is wired', async () => {
    const handlers = callbacks()
    render(RecapScreen, { props: { state: await recapState(), ...handlers }, context: context(true) })
    await fireEvent.change(screen.getByLabelText('Recap period'), { target: { value: 'LAST_MONTH' } })
    expect(handlers.onperiod).toHaveBeenCalledWith('LAST_MONTH')
  })
})
