/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { ArtistInfo, DetailTarget, ItemStats } from '$lib/model/detail'
import type { PlayEvent } from '$lib/model/music'
import type { DataResult } from '$lib/model/result'
import type { HistoryRepository, SpotifyRemote } from './repositories'
import { STREAM_MS, type Accumulator } from './stats'

/** At most this many months in the chart (the most recent ones). */
export const MAX_MONTHS = 24

const pad = (value: number) => String(value).padStart(2, '0')
const monthOf = (epochMs: number) => {
  const date = new Date(epochMs)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
}
const nextMonth = (month: string) => {
  const [year, m] = month.split('-').map(Number) as [number, number]
  return m === 12 ? `${year + 1}-01` : `${year}-${pad(m + 1)}`
}

/** Which plays belong to the item (the WHERE of observeTrack/Artist/AlbumTotals). */
export function matcher(target: DetailTarget): (play: PlayEvent) => boolean {
  switch (target.kind) {
    case 'track':
      return (play) => play.trackName === target.name && play.artistName === target.artist
    case 'artist':
      return (play) => play.artistName === target.name
    case 'album':
      return (play) => play.albumName === target.name && play.artistName === target.artist
  }
}

/** Streams (≥ 30 s), their listening time, first/last play and streams per local month. */
export class ItemStatsAccumulator implements Accumulator<ItemStats> {
  #streams = 0
  #listenedMs = 0
  #first: number | null = null
  #last: number | null = null
  readonly #months = new Map<string, number>()

  constructor(private readonly matches: (play: PlayEvent) => boolean) {}

  add(play: PlayEvent) {
    if (play.msPlayed < STREAM_MS || !this.matches(play)) return
    this.#streams++
    this.#listenedMs += play.msPlayed
    if (this.#first === null || play.playedAt < this.#first) this.#first = play.playedAt
    if (this.#last === null || play.playedAt > this.#last) this.#last = play.playedAt
    const month = monthOf(play.playedAt)
    this.#months.set(month, (this.#months.get(month) ?? 0) + 1)
  }

  result(): ItemStats {
    return {
      streams: this.#streams,
      listenedMs: this.#listenedMs,
      firstPlayed: this.#first,
      lastPlayed: this.#last,
      monthly: this.#series(),
    }
  }

  /** Consecutive months from the first to the last play (gaps as 0), the last MAX_MONTHS of them. */
  #series(): { month: string; streams: number }[] {
    if (this.#first === null || this.#last === null) return []
    const series: { month: string; streams: number }[] = []
    const end = monthOf(this.#last)
    for (let month = monthOf(this.#first); ; month = nextMonth(month)) {
      series.push({ month, streams: this.#months.get(month) ?? 0 })
      if (month === end) break
    }
    return series.slice(-MAX_MONTHS)
  }
}

/** The detail screen's data: your local numbers, plus genres for artists Spotify knows. */
export class GetItemDetail {
  readonly #artists = new Map<string, Promise<DataResult<ArtistInfo>>>()

  constructor(private readonly deps: { history: HistoryRepository; remote: SpotifyRemote; now: () => number }) {}

  async stats(target: DetailTarget): Promise<ItemStats> {
    const accumulator = new ItemStatsAccumulator(matcher(target))
    await this.deps.history.scan(0, this.deps.now() + 60_000, (play) => accumulator.add(play))
    return accumulator.result()
  }

  /** Cached per session: artists rarely change and the Spotify quota is shared. */
  artist(id: string): Promise<DataResult<ArtistInfo>> {
    let request = this.#artists.get(id)
    if (!request) {
      request = this.deps.remote.artist(id)
      this.#artists.set(id, request)
      // Failures are not cached, so a retry can succeed.
      void request.then((result) => !result.ok && this.#artists.delete(id))
    }
    return request
  }
}
