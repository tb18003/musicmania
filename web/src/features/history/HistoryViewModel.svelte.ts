/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { localDateOf, type LocalDate } from '$lib/domain/dates'
import type { HistoryRepository } from '$lib/domain/repositories'
import type { PlayEvent } from '$lib/model/music'

export interface HistoryDay {
  date: LocalDate
  plays: PlayEvent[]
}

export interface HistoryState {
  days: HistoryDay[]
  loading: boolean
  /** No older plays left to load. */
  end: boolean
}

export const PAGE_SIZE = 50

/** Newest-first history in pages, grouped by local day (the web's Paging 3). */
export class HistoryViewModel {
  state = $state<HistoryState>({ days: [], loading: true, end: false })
  readonly #history: HistoryRepository
  #oldest: number | undefined

  constructor(history: HistoryRepository) {
    this.#history = history
  }

  async refresh(): Promise<void> {
    this.#oldest = undefined
    this.state = { days: [], loading: true, end: false }
    await this.loadMore()
  }

  async loadMore(): Promise<void> {
    this.state.loading = true
    const page = await this.#history.recent(PAGE_SIZE, this.#oldest)
    this.#oldest = page.at(-1)?.playedAt ?? this.#oldest
    const days = [...this.state.days]
    for (const play of page) {
      const date = localDateOf(play.playedAt)
      const last = days.at(-1)
      if (last?.date === date) last.plays = [...last.plays, play]
      else days.push({ date, plays: [play] })
    }
    this.state = { days, loading: false, end: page.length < PAGE_SIZE }
  }
}
