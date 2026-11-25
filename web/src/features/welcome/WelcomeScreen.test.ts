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

describe('WelcomeScreen', () => {
  it('in the browser, explains how to install', async () => {
    const oninstall = vi.fn()
    render(WelcomeScreen, { props: { installed: false, oninstall, onsettings: vi.fn() }, context: context() })

    await fireEvent.click(screen.getByRole('button', { name: 'Cómo instalarla' }))

    expect(oninstall).toHaveBeenCalledOnce()
    expect(screen.getByText(/Todo se calcula y se guarda en este dispositivo/)).toBeTruthy()
  })

  it('once installed, the install card is gone', () => {
    render(WelcomeScreen, { props: { installed: true, oninstall: vi.fn(), onsettings: vi.fn() }, context: context() })
    expect(screen.queryByRole('button', { name: 'Cómo instalarla' })).toBeNull()
  })
})
