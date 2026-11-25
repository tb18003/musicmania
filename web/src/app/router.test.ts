/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it, vi } from 'vitest'
import { parseHash, Router } from './router.svelte'

describe('router', () => {
  it('maps hashes to known routes and sends anything else home', () => {
    expect(parseHash('').path).toBe('/')
    expect(parseHash('#/settings').path).toBe('/settings')
    expect(parseHash('#/install').path).toBe('/install')
    expect(parseHash('#/does-not-exist')).toEqual({ path: '/', params: {} })
    expect(parseHash('#javascript:alert(1)').path).toBe('/')
  })

  it('reads query parameters after the path, decoded', () => {
    expect(parseHash('#/detail?kind=track&name=Low%20Tide&artist=Coral%20%26%20Echo')).toEqual({
      path: '/detail',
      params: { kind: 'track', name: 'Low Tide', artist: 'Coral & Echo' },
    })
    expect(parseHash('#/nope?kind=track').params).toEqual({})
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
