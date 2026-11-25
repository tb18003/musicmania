/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { mount } from 'svelte'
import App from './app/App.svelte'
import { Router } from './app/router.svelte'
import { installPlatform, isStandalone } from './lib/ui/platform'
import { Preferences } from './lib/ui/preferences.svelte'
import './lib/ui/tokens.css'

function localStorageOrUndefined(): Storage | undefined {
  try {
    return window.localStorage
  } catch {
    return undefined
  }
}

const target = document.getElementById('app')
if (!target) throw new Error('#app is missing from index.html')

mount(App, {
  target,
  props: {
    preferences: new Preferences({
      storage: localStorageOrUndefined(),
      languages: navigator.languages,
      prefersReducedMotion: () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    }),
    router: new Router(window),
    platform: installPlatform(navigator),
    installed: isStandalone(window, navigator),
    version: __APP_VERSION__,
  },
})
