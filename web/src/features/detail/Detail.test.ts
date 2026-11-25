/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { GetItemDetail } from '$lib/domain/detail'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import { FakeSpotifyRemote, InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import DetailScreen from './DetailScreen.svelte'
import { DetailViewModel, type DetailState } from './DetailViewModel.svelte'

const NOW = new Date(2026, 10, 25).getTime()

describe('DetailViewModel', () => {
  it('an artist with a Spotify id gets genres and picture; others only local numbers', async () => {
    const history = new InMemoryHistoryRepository()
    await history.addPlays([
      play({ artistName: 'Coral Echo', msPlayed: 200_000, playedAt: new Date(2026, 9, 1).getTime() }),
    ])
    const remote = new FakeSpotifyRemote()
    remote.artistInfo.set('ce1', { genres: ['dream pop'], imageUrl: 'https://i.scdn.co/ce' })
    const vm = new DetailViewModel(new GetItemDetail({ history, remote, now: () => NOW }))

    await vm.open({ kind: 'artist', name: 'Coral Echo', artist: '', id: 'ce1', imageUrl: null })
    expect(vm.state).toMatchObject({ loading: false, genres: ['dream pop'], imageUrl: 'https://i.scdn.co/ce' })
    expect(vm.state.stats?.streams).toBe(1)

    await vm.open({ kind: 'track', name: 'Low Tide', artist: 'Coral Echo', id: null, imageUrl: null })
    expect(vm.state.genres).toEqual([])
    expect(remote.artistCalls).toEqual(['ce1'])
  })
})

const context = () =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['en'], prefersReducedMotion: () => true }))
const state = (overrides: Partial<DetailState> = {}): DetailState => ({
  target: { kind: 'track', name: 'Low Tide', artist: 'Coral Echo', id: 'lowtide1', imageUrl: null },
  loading: false,
  stats: {
    streams: 1_234,
    listenedMs: 3_600_000,
    firstPlayed: new Date(2025, 0, 3).getTime(),
    lastPlayed: new Date(2026, 10, 20).getTime(),
    monthly: [
      { month: '2026-09', streams: 3 },
      { month: '2026-10', streams: 9 },
      { month: '2026-11', streams: 4 },
    ],
  },
  genres: [],
  imageUrl: null,
  ...overrides,
})

describe('DetailScreen', () => {
  it('shows your numbers, a described monthly chart and a safe link to Spotify', () => {
    render(DetailScreen, { props: { state: state(), onback: vi.fn() }, context: context() })
    expect(screen.getByRole('heading', { name: 'Low Tide' })).toBeTruthy()
    expect(screen.getByText('1,234')).toBeTruthy()
    expect(screen.getByText('60')).toBeTruthy()
    expect(screen.getByRole('img', { name: /Most in October 2026: 9/ })).toBeTruthy()
    const link = screen.getByRole('link', { name: 'Open in Spotify' }) as HTMLAnchorElement
    expect(link.href).toBe('https://open.spotify.com/track/lowtide1')
    expect(link.rel).toBe('noopener noreferrer')
  })

  it('an item never streamed says so; genres show for artists', () => {
    render(DetailScreen, {
      props: {
        state: state({
          target: { kind: 'artist', name: 'Coral Echo', artist: '', id: null, imageUrl: null },
          stats: { streams: 0, listenedMs: 0, firstPlayed: null, lastPlayed: null, monthly: [] },
          genres: ['dream pop', 'shoegaze'],
        }),
        onback: vi.fn(),
      },
      context: context(),
    })
    expect(screen.getByText(/No streams of this in your history/)).toBeTruthy()
    expect(screen.getByRole('list', { name: 'Genres' }).textContent).toContain('shoegaze')
    expect(screen.queryByRole('link', { name: 'Open in Spotify' })).toBeNull()
  })
})
