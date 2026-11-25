/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import HomeScreen from './HomeScreen.svelte'
import type { HomeState } from './HomeViewModel.svelte'

const NOW = Date.UTC(2026, 10, 25, 18)
const context = (language: 'en' | 'es' = 'en') =>
  uiContextFor(new Preferences({ storage: undefined, languages: [language], prefersReducedMotion: () => true }))

const state = (overrides: Partial<HomeState> = {}): HomeState => ({
  loading: false,
  profile: { id: 'ana', displayName: 'Ana', imageUrl: null },
  today: { listenedMs: 1_260_000, streams: 1, uniqueTracks: 1, uniqueArtists: 1 },
  week: { listenedMs: 74_400_000, streams: 1_204, uniqueTracks: 300, uniqueArtists: 80 },
  streaks: { current: 1, longest: 12, longestEndedOn: null },
  topArtist: { artistName: 'Coral Echo', streams: 42, imageUrl: null },
  nowPlaying: null,
  totalPlays: 1_500,
  sync: { running: false, lastSuccess: NOW - 5 * 60_000, possibleGap: false },
  error: null,
  ...overrides,
})

describe('HomeScreen', () => {
  it('shows today, the week and the streak with correct plurals and grouping', () => {
    render(HomeScreen, {
      props: { state: state(), now: NOW, onsync: vi.fn(), onimport: vi.fn(), onsettings: vi.fn() },
      context: context(),
    })
    expect(screen.getByRole('heading', { name: 'Hi, Ana' })).toBeTruthy()
    expect(screen.getByText('Synced 5 minutes ago')).toBeTruthy()
    expect(screen.getByText('1 stream')).toBeTruthy()
    expect(screen.getByText('1,204 streams')).toBeTruthy()
    expect(screen.getByText('1,240')).toBeTruthy() // 74,400,000 ms = 1,240 min
    expect(screen.getByText('day')).toBeTruthy()
    expect(screen.getByText('Best: 12 days')).toBeTruthy()
    expect(screen.getByText('Coral Echo')).toBeTruthy()
  })

  it('a brand-new history explains how it fills up', () => {
    render(HomeScreen, {
      props: { state: state({ totalPlays: 0 }), now: NOW, onsync: vi.fn(), onimport: vi.fn(), onsettings: vi.fn() },
      context: context('es'),
    })
    expect(screen.getByRole('heading', { name: 'Tu historial empieza ahora' })).toBeTruthy()
  })

  it('warns about a possible gap, and errors take precedence', () => {
    const gap = state({ sync: { running: false, lastSuccess: NOW, possibleGap: true } })
    const { unmount } = render(HomeScreen, {
      props: { state: gap, now: NOW, onsync: vi.fn(), onimport: vi.fn(), onsettings: vi.fn() },
      context: context(),
    })
    expect(screen.getByRole('status').textContent).toContain('more than 50 songs')
    unmount()

    render(HomeScreen, {
      props: {
        state: { ...gap, error: { kind: 'forbidden' } },
        now: NOW,
        onsync: vi.fn(),
        onimport: vi.fn(),
        onsettings: vi.fn(),
      },
      context: context(),
    })
    expect(screen.getByRole('alert').textContent).toContain('Spotify refused this account')
  })

  it('"Sync now" is disabled while a sync runs', async () => {
    const onsync = vi.fn()
    const { unmount } = render(HomeScreen, {
      props: { state: state(), now: NOW, onsync, onimport: vi.fn(), onsettings: vi.fn() },
      context: context(),
    })
    await fireEvent.click(screen.getByRole('button', { name: 'Sync now' }))
    expect(onsync).toHaveBeenCalledOnce()
    unmount()

    const running = state({ sync: { running: true, lastSuccess: null, possibleGap: false } })
    render(HomeScreen, {
      props: { state: running, now: NOW, onsync, onimport: vi.fn(), onsettings: vi.fn() },
      context: context(),
    })
    expect((screen.getByRole('button', { name: 'Sync now' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('Syncing…')).toBeTruthy()
  })

  it('Settings is the gear in the header, with an accessible name', async () => {
    const onsettings = vi.fn()
    render(HomeScreen, {
      props: { state: state(), now: NOW, onsync: vi.fn(), onimport: vi.fn(), onsettings },
      context: context(),
    })
    await fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
    expect(onsettings).toHaveBeenCalledOnce()
  })
})
