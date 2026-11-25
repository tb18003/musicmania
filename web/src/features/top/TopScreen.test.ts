/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import TopScreen from './TopScreen.svelte'
import type { TopState } from './TopViewModel.svelte'

const NOW = Date.UTC(2026, 10, 25, 12)
const context = () =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['en'], prefersReducedMotion: () => true }))
const callbacks = () => ({ onkind: vi.fn(), onrange: vi.fn(), onrefresh: vi.fn(), onimport: vi.fn() })

const row = (rank: number, title: string, movement: TopState['rows'][number]['ranked']['movement']) => ({
  key: title,
  title,
  subtitle: 'Coral Echo',
  imageUrl: null,
  ranked: { rank, item: null, movement, streams: null, listenedMs: null },
  detail: { kind: 'track' as const, name: title, artist: 'Coral Echo', id: null, imageUrl: null },
})

const state = (overrides: Partial<TopState> = {}): TopState => ({
  kind: 'tracks',
  range: 'FOUR_WEEKS',
  loading: false,
  rows: [
    row(1, 'Low Tide', { kind: 'up', places: 2 }),
    row(2, 'High Tide', { kind: 'new' }),
    row(3, 'Mid Tide', { kind: 'down', places: 1 }),
  ],
  fetchedAt: NOW - 2 * 3_600_000,
  error: null,
  ...overrides,
})

describe('TopScreen', () => {
  it('ranks with accessible movement badges and the update time', () => {
    render(TopScreen, { props: { state: state(), now: NOW, ...callbacks() }, context: context() })
    expect(screen.getByText('From Spotify, updated 2 hours ago')).toBeTruthy()
    expect(screen.getByRole('img', { name: 'Up 2 places' }).textContent).toBe('▲2')
    expect(screen.getByRole('img', { name: 'Down 1 place' }).textContent).toBe('▼1')
    expect(screen.getByRole('img', { name: 'New' })).toBeTruthy()
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
  })

  it('kind, period and refresh are wired', async () => {
    const handlers = callbacks()
    render(TopScreen, { props: { state: state(), now: NOW, ...handlers }, context: context() })
    await fireEvent.click(screen.getByLabelText('Artists'))
    await fireEvent.click(screen.getByLabelText('All time'))
    await fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(handlers.onkind).toHaveBeenCalledWith('artists')
    expect(handlers.onrange).toHaveBeenCalledWith('LIFETIME')
    expect(handlers.onrefresh).toHaveBeenCalledOnce()
  })

  it('an empty all-time top invites to import the Spotify history', async () => {
    const handlers = callbacks()
    render(TopScreen, {
      props: { state: state({ range: 'LIFETIME', rows: [], fetchedAt: null }), now: NOW, ...handlers },
      context: context(),
    })
    expect(screen.getByText('From your history on this device')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Refresh' })).toBeNull()
    await fireEvent.click(screen.getByRole('button', { name: 'Import my Spotify history' }))
    expect(handlers.onimport).toHaveBeenCalledOnce()
  })
})
