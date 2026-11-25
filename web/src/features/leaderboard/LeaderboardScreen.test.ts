/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import LeaderboardScreen from './LeaderboardScreen.svelte'
import type { LeaderboardState } from './LeaderboardViewModel.svelte'

const context = () =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['es'], prefersReducedMotion: () => true }))
const callbacks = () => ({
  onmetric: vi.fn(),
  onperiod: vi.fn(),
  onenablesharing: vi.fn(),
  onfriend: vi.fn(),
  onretry: vi.fn(),
  oncopy: vi.fn(async () => true),
})
const state = (overrides: Partial<LeaderboardState>): LeaderboardState => ({
  metric: 'minutes',
  period: 'week',
  content: { kind: 'loading' },
  expanded: null,
  enablingSharing: false,
  ...overrides,
})

describe('LeaderboardScreen', () => {
  it('ranks friends, marks me as "Tú" and formats the metric', () => {
    render(LeaderboardScreen, {
      props: {
        state: state({
          content: {
            kind: 'ranking',
            entries: [
              { rank: 1, spotifyUserId: 'ana', displayName: 'Ana', avatarUrl: null, value: 12_510, isMe: false },
              { rank: 2, spotifyUserId: 'me', displayName: 'Me', avatarUrl: null, value: 1_240, isMe: true },
            ],
          },
        }),
        ...callbacks(),
      },
      context: context(),
    })
    expect(screen.getByRole('button', { name: 'Detalles de Ana' }).textContent).toContain('12.510 min') // Spanish groups from 5 digits (CLDR)
    expect(screen.getByRole('button', { name: 'Detalles de Tú' })).toBeTruthy()
  })

  it('opting in is one tap, with what is shared spelled out', async () => {
    const handlers = callbacks()
    render(LeaderboardScreen, {
      props: { state: state({ content: { kind: 'sharingOff' } }), ...handlers },
      context: context(),
    })
    expect(screen.getByText(/Nunca tu historial de escuchas/)).toBeTruthy()
    await fireEvent.click(screen.getByRole('button', { name: 'Compartir mis estadísticas' }))
    expect(handlers.onenablesharing).toHaveBeenCalledOnce()
  })

  it('outside the allowlist: shows the Spotify ID and copies it', async () => {
    const handlers = callbacks()
    render(LeaderboardScreen, {
      props: { state: state({ content: { kind: 'notAllowlisted', spotifyUserId: 'ana_01' } }), ...handlers },
      context: context(),
    })
    expect(screen.getByText('ana_01')).toBeTruthy()
    await fireEvent.click(screen.getByRole('button', { name: 'Copiar ID de Spotify' }))
    expect(handlers.oncopy).toHaveBeenCalledWith('ana_01')
    expect(await screen.findByRole('button', { name: 'Copiado' })).toBeTruthy()
  })

  it('an empty ranking says nobody shares yet; errors offer a retry', async () => {
    const handlers = callbacks()
    const { unmount } = render(LeaderboardScreen, {
      props: { state: state({ content: { kind: 'ranking', entries: [] } }), ...handlers },
      context: context(),
    })
    expect(screen.getByRole('heading', { name: 'Nadie comparte todavía' })).toBeTruthy()
    unmount()

    render(LeaderboardScreen, {
      props: { state: state({ content: { kind: 'error', error: { kind: 'network' } } }), ...handlers },
      context: context(),
    })
    await fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(handlers.onretry).toHaveBeenCalledOnce()
  })
})
