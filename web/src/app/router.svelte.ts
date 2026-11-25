/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { parseHash, type Route } from './location'

export { parseHash, ROUTES, type Location, type Route } from './location'

/**
 * Hash routing: GitHub Pages serves a single index.html, so there are no 404s to work around
 * and the browser's back button works.
 */
export class Router {
  current = $state<Route>('/')
  params = $state<Readonly<Record<string, string>>>({})
  readonly #win: Window

  constructor(win: Window) {
    this.#win = win
    this.#read()
    win.addEventListener('hashchange', () => {
      this.#read()
      win.scrollTo(0, 0)
    })
  }

  #read() {
    const { path, params } = parseHash(this.#win.location.hash)
    this.current = path
    this.params = params
  }

  navigate(route: Route) {
    this.#win.location.hash = route
  }

  back() {
    if (this.#win.history.length > 1) this.#win.history.back()
    else this.navigate('/')
  }
}
