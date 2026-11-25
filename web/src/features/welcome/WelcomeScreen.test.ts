/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import WelcomeScreen from './WelcomeScreen.svelte'

const context = () =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['es'], prefersReducedMotion: () => true }))

const base = {
  installed: false,
  canSignIn: true,
  signingIn: false,
  message: null,
  onsignin: vi.fn(),
  oninstall: vi.fn(),
  onsettings: vi.fn(),
}

describe('WelcomeScreen', () => {
  it('in the browser, explains how to install', async () => {
    const oninstall = vi.fn()
    render(WelcomeScreen, { props: { ...base, installed: false, oninstall }, context: context() })

    await fireEvent.click(screen.getByRole('button', { name: 'Cómo instalarla' }))

    expect(oninstall).toHaveBeenCalledOnce()
    expect(screen.getByText(/Todo se calcula y se guarda en este dispositivo/)).toBeTruthy()
  })

  it('once installed, the install card is gone', () => {
    render(WelcomeScreen, { props: { ...base, installed: true }, context: context() })
    expect(screen.queryByRole('button', { name: 'Cómo instalarla' })).toBeNull()
  })

  it('signs in with Spotify, and explains when sign-in is unavailable', async () => {
    const onsignin = vi.fn()
    const { unmount } = render(WelcomeScreen, { props: { ...base, onsignin }, context: context() })
    await fireEvent.click(screen.getByRole('button', { name: 'Entrar con Spotify' }))
    expect(onsignin).toHaveBeenCalledOnce()
    unmount()

    render(WelcomeScreen, { props: { ...base, canSignIn: false }, context: context() })
    expect((screen.getByRole('button', { name: 'Entrar con Spotify' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('El acceso no está configurado en esta versión.')).toBeTruthy()
  })

  it('tells the user why they are back on this screen', () => {
    render(WelcomeScreen, { props: { ...base, message: 'welcome_session_expired' }, context: context() })
    expect(screen.getByRole('status').textContent).toContain('Tu sesión ha caducado')
  })
})
