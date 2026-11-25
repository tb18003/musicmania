/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it, vi } from 'vitest'
import { parseHash, Router } from './router.svelte'

describe('router', () => {
  it('maps hashes to known routes and sends anything else home', () => {
    expect(parseHash('')).toBe('/')
    expect(parseHash('#/settings')).toBe('/settings')
    expect(parseHash('#/install')).toBe('/install')
    expect(parseHash('#/does-not-exist')).toBe('/')
    expect(parseHash('#javascript:alert(1)')).toBe('/')
  })

  it('follows hash changes and starts each screen at the top', () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
    window.location.hash = ''
    const router = new Router(window)
    expect(router.current).toBe('/')

    router.navigate('/settings')
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    expect(router.current).toBe('/settings')
    expect(scrollTo).toHaveBeenCalledWith(0, 0)
  })
})
