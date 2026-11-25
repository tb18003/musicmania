/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { HistoryRepository } from '$lib/domain/repositories'
import { buildPages, buildRecap, recapRange, type Recap, type RecapPeriod, type StoryPage } from '$lib/domain/recap'

export interface RecapState {
  period: RecapPeriod
  loading: boolean
  recap: Recap | null
  pages: StoryPage[]
}

export class RecapViewModel {
  state = $state<RecapState>({ period: 'THIS_YEAR', loading: true, recap: null, pages: [] })
  readonly #deps: { history: HistoryRepository; now: () => number }
  #request = 0

  constructor(deps: { history: HistoryRepository; now: () => number }) {
    this.#deps = deps
  }

  select(period: RecapPeriod) {
    this.state.period = period
    return this.load()
  }

  async load(): Promise<void> {
    const request = ++this.#request
    const period = this.state.period
    this.state.loading = true
    const now = this.#deps.now()
    const recap = await buildRecap(this.#deps.history, recapRange(period, now), now)
    if (request === this.#request) this.state = { period, loading: false, recap, pages: buildPages(recap) }
  }
}
