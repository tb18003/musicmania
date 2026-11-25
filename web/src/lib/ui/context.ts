/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { getContext, setContext } from 'svelte'
import type { Preferences } from './preferences.svelte'

const KEY = Symbol('musicmania.ui')

/** Provided once by the app shell: strings, theme and motion preferences for every screen. */
export function provideUi(preferences: Preferences) {
  setContext(KEY, preferences)
}

export function useUi(): Preferences {
  const preferences = getContext<Preferences | undefined>(KEY)
  if (!preferences) throw new Error('useUi() called outside the app shell')
  return preferences
}

/** For tests: the context map that render() needs to mount a screen outside the shell. */
export function uiContextFor(preferences: Preferences): Map<symbol, Preferences> {
  return new Map([[KEY, preferences]])
}
