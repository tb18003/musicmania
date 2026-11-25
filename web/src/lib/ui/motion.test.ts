/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it, vi } from 'vitest'
import { createEntranceRegistry, entrance } from './motion'

describe('entrance once per session', () => {
  it('plays the first time a screen is shown, never again', () => {
    const registry = createEntranceRegistry()
    expect(registry.shouldPlay('top')).toBe(true)
    expect(registry.shouldPlay('top')).toBe(false)
    expect(registry.shouldPlay('home')).toBe(true)
  })

  it('animates with a stagger only when asked to play', () => {
    const node = document.createElement('div')
    const animate = vi.fn()
    node.animate = animate

    entrance(node, { play: false })
    expect(animate).not.toHaveBeenCalled()

    entrance(node, { play: true, index: 2 })
    expect(animate).toHaveBeenCalledOnce()
    expect(animate.mock.calls[0]?.[1]).toMatchObject({ delay: 90, fill: 'backwards' })
  })
})
