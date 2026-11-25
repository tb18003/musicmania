/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { GetInsights, Insights } from '$lib/domain/insights'
import type { TimeRange } from '$lib/model/top'

export interface InsightsState {
  range: TimeRange
  loading: boolean
  insights: Insights | null
}

export class InsightsViewModel {
  state = $state<InsightsState>({ range: 'FOUR_WEEKS', loading: true, insights: null })
  readonly #insights: GetInsights
  #request = 0

  constructor(insights: GetInsights) {
    this.#insights = insights
  }

  selectRange(range: TimeRange) {
    this.state.range = range
    return this.load()
  }

  async load(): Promise<void> {
    const request = ++this.#request
    const range = this.state.range
    this.state.loading = true
    const insights = await this.#insights.load(range)
    // Switching ranges quickly: only the latest answer may update the screen.
    if (request === this.#request) this.state = { range, loading: false, insights }
  }
}
