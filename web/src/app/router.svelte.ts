/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** Every destination, like the @Serializable routes in MusicManiaNavHost. */
export const ROUTES = ['/', '/top', '/ranking', '/history', '/import', '/install', '/settings'] as const
export type Route = (typeof ROUTES)[number]

/** "#/settings" → "/settings". Anything unknown (or tampered with) goes home. */
export function parseHash(hash: string): Route {
  const path = hash.replace(/^#/, '') || '/'
  return (ROUTES as readonly string[]).includes(path) ? (path as Route) : '/'
}

/**
 * Hash routing: GitHub Pages serves a single index.html, so there are no 404s to work around
 * and the browser's back button works.
 */
export class Router {
  current = $state<Route>('/')
  readonly #win: Window

  constructor(win: Window) {
    this.#win = win
    this.current = parseHash(win.location.hash)
    win.addEventListener('hashchange', () => {
      this.current = parseHash(win.location.hash)
      win.scrollTo(0, 0)
    })
  }

  navigate(route: Route) {
    this.#win.location.hash = route
  }

  back() {
    if (this.#win.history.length > 1) this.#win.history.back()
    else this.navigate('/')
  }
}
