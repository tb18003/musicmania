/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { GetItemDetail } from '$lib/domain/detail'
import type { DetailTarget, ItemStats } from '$lib/model/detail'

export interface DetailState {
  target: DetailTarget | null
  loading: boolean
  stats: ItemStats | null
  genres: readonly string[]
  /** The best picture: the one the link carried, or Spotify's for an artist. */
  imageUrl: string | null
}

export class DetailViewModel {
  state = $state<DetailState>({ target: null, loading: true, stats: null, genres: [], imageUrl: null })
  readonly #detail: GetItemDetail
  #request = 0

  constructor(detail: GetItemDetail) {
    this.#detail = detail
  }

  async open(target: DetailTarget): Promise<void> {
    const request = ++this.#request
    this.state = { target, loading: true, stats: null, genres: [], imageUrl: target.imageUrl }
    const [stats, artist] = await Promise.all([
      this.#detail.stats(target),
      target.kind === 'artist' && target.id ? this.#detail.artist(target.id) : Promise.resolve(null),
    ])
    if (request !== this.#request) return
    this.state = {
      target,
      loading: false,
      stats,
      genres: artist?.ok ? artist.data.genres : [],
      imageUrl: target.imageUrl ?? (artist?.ok ? artist.data.imageUrl : null),
    }
  }
}
