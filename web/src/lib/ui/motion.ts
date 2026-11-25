/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { Action } from 'svelte/action'

/**
 * Entrance animations play once per session per screen, like ProvideEntranceOnce in the app:
 * coming back to a tab shows it settled. A "session" is the life of the page (a reload resets it).
 */
export function createEntranceRegistry() {
  const seen = new Set<string>()
  return {
    /** True the first time a key is asked for; false afterwards. */
    shouldPlay(key: string): boolean {
      if (seen.has(key)) return false
      seen.add(key)
      return true
    },
  }
}

export const entrances = createEntranceRegistry()

const STAGGER_MS = 45
const DURATION_MS = 320
const OFFSET_PX = 14

export interface EntranceOptions {
  /** Whether this screen plays its entrance (first visit and motion allowed). */
  play: boolean
  /** Position in the stagger sequence. */
  index?: number
}

/** Staggered fade + rise, the web's staggeredEntrance. Does nothing when play is false. */
export const entrance: Action<HTMLElement, EntranceOptions> = (node, options) => {
  if (!options.play || typeof node.animate !== 'function') return
  node.animate(
    [
      { opacity: 0, transform: `translateY(${OFFSET_PX}px)` },
      { opacity: 1, transform: 'none' },
    ],
    {
      duration: DURATION_MS,
      delay: (options.index ?? 0) * STAGGER_MS,
      easing: 'cubic-bezier(0.2, 0, 0, 1)',
      fill: 'backwards',
    },
  )
}
