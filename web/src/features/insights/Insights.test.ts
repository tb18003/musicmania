/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { GetInsights, type Insights } from '$lib/domain/insights'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import { InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import InsightsScreen from './InsightsScreen.svelte'
import { InsightsViewModel, type InsightsState } from './InsightsViewModel.svelte'

const NOW = new Date(2026, 10, 25, 18).getTime()

describe('InsightsViewModel', () => {
  it('loads the 4-week insights and reloads when the range changes', async () => {
    const history = new InMemoryHistoryRepository()
    await history.addPlays([
      play({ playedAt: new Date(2026, 10, 24, 22).getTime(), msPlayed: 600_000 }),
      play({ playedAt: new Date(2025, 0, 10, 8).getTime(), msPlayed: 600_000, trackName: 'Old' }),
    ])
    const vm = new InsightsViewModel(new GetInsights({ history, now: () => NOW }))

    await vm.load()
    expect(vm.state.insights?.comparison.current.streams).toBe(1)
    expect(vm.state.insights?.personality).toBe('EVENING_DRIFTER')

    await vm.selectRange('LIFETIME')
    expect(vm.state.range).toBe('LIFETIME')
    expect(vm.state.insights?.comparison.current.streams).toBe(2)
    expect(vm.state.loading).toBe(false)
  })
})

const clock = Array.from({ length: 24 }, (_, h) => (h === 21 ? 3_600_000 : h === 9 ? 600_000 : 0))
const heatmap = Array.from({ length: 7 }, (_, d) => Array.from({ length: 24 }, (_, h) => (d === 0 && h === 21 ? 5 : 0)))
const summary = (listenedMs: number) => ({ listenedMs, streams: 10, uniqueTracks: 5, uniqueArtists: 3 })
const insights = (overrides: Partial<Insights> = {}): Insights => ({
  clock,
  heatmap,
  habits: { skipRate: 0.25, shuffleRate: 0.6, platforms: [{ platform: 'iOS', listenedMs: 100 }] },
  personality: 'EVENING_DRIFTER',
  streaks: { current: 3, longest: 12, longestEndedOn: null },
  milestones: [
    { type: 'MINUTES', threshold: 1_000, value: 1_200, achieved: true, progress: 1 },
    { type: 'MINUTES', threshold: 10_000, value: 1_200, achieved: false, progress: 0.12 },
  ],
  comparison: { current: summary(150), previous: summary(100) },
  hasHistory: true,
  ...overrides,
})
const context = () =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['en'], prefersReducedMotion: () => true }))
const render_ = (state: InsightsState, onimport = vi.fn(), onrange = vi.fn()) =>
  render(InsightsScreen, { props: { state, onrange, onimport }, context: context() })

describe('InsightsScreen', () => {
  it('describes every chart for screen readers', () => {
    render_({ range: 'FOUR_WEEKS', loading: false, insights: insights() })
    expect(screen.getByText('Evening drifter')).toBeTruthy()
    expect(screen.getByRole('img', { name: /You listen most at 9\sPM/ })).toBeTruthy()
    expect(screen.getByRole('img', { name: /Busiest: Monday at 9\sPM/ })).toBeTruthy()
    expect(screen.getByRole('img', { name: 'Skipped: 25% of plays' })).toBeTruthy()
    expect(screen.getByRole('img', { name: 'Shuffle: 60% of plays' })).toBeTruthy()
  })

  it('shows the change versus the previous period, streaks and the next goals', () => {
    render_({ range: 'FOUR_WEEKS', loading: false, insights: insights() })
    expect(screen.getByText('+50% listening time')).toBeTruthy()
    expect(screen.getByText('Longest: 12')).toBeTruthy()
    expect(screen.getByText('1 milestone achieved')).toBeTruthy()
    expect(screen.getByText('1,200 of 10,000 minutes')).toBeTruthy()
  })

  it('synced-only history explains where skip and shuffle data come from', () => {
    render_({
      range: 'FOUR_WEEKS',
      loading: false,
      insights: insights({ habits: { skipRate: null, shuffleRate: null, platforms: [] } }),
    })
    expect(screen.getByText(/come from the imported Spotify history/)).toBeTruthy()
  })

  it('an empty history invites to import; the range control is wired', async () => {
    const onimport = vi.fn()
    const onrange = vi.fn()
    render_({ range: 'FOUR_WEEKS', loading: false, insights: insights({ hasHistory: false }) }, onimport, onrange)
    await fireEvent.click(screen.getByRole('button', { name: 'Import my Spotify history' }))
    await fireEvent.click(screen.getByLabelText('All time'))
    expect(onimport).toHaveBeenCalledOnce()
    expect(onrange).toHaveBeenCalledWith('LIFETIME')
  })
})
