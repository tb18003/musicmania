/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { GetTopItems, TopResult } from '$lib/domain/top'
import type { AppError } from '$lib/model/result'
import type { Ranked, TimeRange, TopAlbum, TopArtist, TopKind, TopTrack } from '$lib/model/top'

/** One row, whatever the kind: the screen only needs a title, a subtitle and an image. */
export interface TopRow {
  key: string
  title: string
  subtitle: string | null
  imageUrl: string | null
  ranked: Ranked<unknown>
}

export interface TopState {
  kind: TopKind
  range: TimeRange
  loading: boolean
  rows: TopRow[]
  fetchedAt: number | null
  error: AppError | null
}

const toRows = {
  tracks: (result: TopResult<TopTrack>) =>
    result.items.map((r) => ({
      key: `${r.item.id ?? r.item.name}|${r.item.artistName}`,
      title: r.item.name,
      subtitle: r.item.artistName,
      imageUrl: r.item.imageUrl,
      ranked: r,
    })),
  artists: (result: TopResult<TopArtist>) =>
    result.items.map((r) => ({
      key: r.item.id ?? r.item.name,
      title: r.item.name,
      subtitle: r.item.genres.slice(0, 2).join(', ') || null,
      imageUrl: r.item.imageUrl,
      ranked: r,
    })),
  albums: (result: TopResult<TopAlbum>) =>
    result.items.map((r) => ({
      key: r.item.key,
      title: r.item.name,
      subtitle: r.item.artistName,
      imageUrl: r.item.imageUrl,
      ranked: r,
    })),
}

export class TopViewModel {
  state = $state<TopState>({
    kind: 'tracks',
    range: 'FOUR_WEEKS',
    loading: true,
    rows: [],
    fetchedAt: null,
    error: null,
  })
  readonly #top: GetTopItems
  #request = 0

  constructor(top: GetTopItems) {
    this.#top = top
  }

  select(kind: TopKind) {
    this.state.kind = kind
    return this.load()
  }

  selectRange(range: TimeRange) {
    this.state.range = range
    return this.load()
  }

  refresh() {
    return this.load(true)
  }

  async load(refresh = false): Promise<void> {
    // Fast switching between tabs: only the latest request may update the screen.
    const request = ++this.#request
    const { kind, range } = this.state
    this.state.loading = true
    const { rows, fetchedAt, error } = await this.#fetch(kind, range, refresh)
    if (request !== this.#request) return
    this.state = { kind, range, loading: false, rows, fetchedAt, error }
  }

  async #fetch(kind: TopKind, range: TimeRange, refresh: boolean) {
    switch (kind) {
      case 'tracks': {
        const result = await this.#top.tracks(range, refresh)
        return { rows: toRows.tracks(result), fetchedAt: result.fetchedAt, error: result.error }
      }
      case 'artists': {
        const result = await this.#top.artists(range, refresh)
        return { rows: toRows.artists(result), fetchedAt: result.fetchedAt, error: result.error }
      }
      case 'albums': {
        const result = await this.#top.albums(range, refresh)
        return { rows: toRows.albums(result), fetchedAt: result.fetchedAt, error: result.error }
      }
    }
  }
}
