/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import { play } from '$testing/fixtures'
import HistoryScreen from './HistoryScreen.svelte'

const NOW = new Date(2026, 10, 25, 18).getTime()
const context = () =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['es'], prefersReducedMotion: () => true }))

describe('HistoryScreen', () => {
  it('labels today, yesterday and older days, newest first', () => {
    const days = [
      { date: '2026-11-25', plays: [play({ trackName: 'Low Tide', playedAt: new Date(2026, 10, 25, 9).getTime() })] },
      {
        date: '2026-11-24',
        plays: [play({ trackName: 'Night Drive', playedAt: new Date(2026, 10, 24, 9).getTime() })],
      },
      { date: '2026-11-20', plays: [play({ trackName: 'Old One', playedAt: new Date(2026, 10, 20, 9).getTime() })] },
    ]
    render(HistoryScreen, {
      props: { state: { days, loading: false, end: true }, now: NOW, onloadmore: vi.fn() },
      context: context(),
    })
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings).toEqual(['Hoy', 'Ayer', 'viernes, 20 de noviembre'])
    expect(screen.getByText('Night Drive')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Cargar más' })).toBeNull()
  })

  it('offers older plays until the end, and explains an empty history', async () => {
    const onloadmore = vi.fn()
    const days = [{ date: '2026-11-25', plays: [play()] }]
    const { unmount } = render(HistoryScreen, {
      props: { state: { days, loading: false, end: false }, now: NOW, onloadmore },
      context: context(),
    })
    await fireEvent.click(screen.getByRole('button', { name: 'Cargar más' }))
    expect(onloadmore).toHaveBeenCalledOnce()
    unmount()

    render(HistoryScreen, {
      props: { state: { days: [], loading: false, end: true }, now: NOW, onloadmore },
      context: context(),
    })
    expect(screen.getByText(/Aún no hay escuchas/)).toBeTruthy()
  })
})
