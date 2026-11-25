/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import SettingsScreen from './SettingsScreen.svelte'

const preferences = () =>
  new Preferences({ storage: undefined, languages: ['en-US'], prefersReducedMotion: () => true })

describe('SettingsScreen', () => {
  it('changing the language translates the screen right away', async () => {
    const prefs = preferences()
    render(SettingsScreen, {
      props: { version: '0.1.0', account: null, onback: vi.fn() },
      context: uiContextFor(prefs),
    })
    expect(screen.getByRole('heading', { name: 'Settings' })).toBeTruthy()

    await fireEvent.click(screen.getByLabelText('Español'))

    expect(prefs.language).toBe('es')
    expect(screen.getByRole('heading', { name: 'Ajustes' })).toBeTruthy()
    expect(screen.getByText('Versión 0.1.0')).toBeTruthy()
  })

  it('theme and animation choices are radio groups with the current value checked', async () => {
    const prefs = preferences()
    render(SettingsScreen, {
      props: { version: '0.1.0', account: null, onback: vi.fn() },
      context: uiContextFor(prefs),
    })

    await fireEvent.click(screen.getByLabelText('Dark'))
    await fireEvent.click(screen.getByLabelText('Full'))

    expect(prefs.theme).toBe('dark')
    expect(prefs.motion).toBe('full')
    expect((screen.getByLabelText('Dark') as HTMLInputElement).checked).toBe(true)
  })

  it('the back button has an accessible name', async () => {
    const onback = vi.fn()
    render(SettingsScreen, { props: { version: '0.1.0', account: null, onback }, context: uiContextFor(preferences()) })
    await fireEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(onback).toHaveBeenCalledOnce()
  })

  it('signing out asks for confirmation first', async () => {
    const onsignout = vi.fn()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    render(SettingsScreen, {
      props: { version: '0.1.0', account: { displayName: 'Ana' }, onsignout },
      context: uiContextFor(preferences()),
    })
    expect(screen.getByText('Signed in as Ana')).toBeTruthy()

    await fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(onsignout).not.toHaveBeenCalled()
    await fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(onsignout).toHaveBeenCalledOnce()
    expect(confirm).toHaveBeenCalledWith('Sign out and delete all MusicMania data from this device?')
  })
})
