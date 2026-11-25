/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { PlayEvent } from '$lib/model/music'
import type { AppError } from '$lib/model/result'
import {
  isApiBacked,
  type RankMovement,
  type Ranked,
  type TimeRange,
  type TopAlbum,
  type TopArtist,
  type TopList,
  type TopTrack,
} from '$lib/model/top'
import type { HistoryRepository, TopItemsRepository } from './repositories'
import { feed, STREAM_MS, type Accumulator } from './stats'

export const TOP_LIST_SIZE = 50

export const trackKey = (track: TopTrack) => track.id ?? `${track.name}|${track.artistName}`
export const artistKey = (artist: TopArtist) => artist.id ?? artist.name

/** Port of RankMovementCalculator: movement against the previous snapshot's 1-based ranks. */
export function rankMovements(
  currentKeys: readonly string[],
  previousRanks: ReadonlyMap<string, number> | null,
): RankMovement[] {
  if (!previousRanks || previousRanks.size === 0) return currentKeys.map(() => ({ kind: 'unknown' }))
  return currentKeys.map((key, index) => {
    const current = index + 1
    const previous = previousRanks.get(key)
    if (previous === undefined) return { kind: 'new' }
    if (previous === current) return { kind: 'same' }
    return previous > current
      ? { kind: 'up', places: previous - current }
      : { kind: 'down', places: current - previous }
  })
}

export function rankList<T>(list: TopList<T>, key: (item: T) => string): Ranked<T>[] {
  const movements = rankMovements(list.items.map(key), list.previousRanks)
  return list.items.map((item, index) => ({
    rank: index + 1,
    item,
    movement: movements[index] ?? { kind: 'unknown' },
    streams: null,
    listenedMs: null,
  }))
}

/**
 * Port of TopDerivations.albumsFromTracks: a Borda count where the track ranked i (0-based) of n
 * gives its album n − i points. Ties keep first-seen order (Array.prototype.sort is stable).
 */
export function albumsFromTracks(tracks: readonly TopTrack[]): TopAlbum[] {
  const n = tracks.length
  const albums = new Map<string, TopAlbum>()
  tracks.forEach((track, index) => {
    const key = track.albumId ?? `${track.albumName ?? ''}|${track.artistName}`
    const existing = albums.get(key)
    albums.set(key, {
      key,
      name: existing?.name ?? track.albumName ?? '',
      artistName: existing?.artistName ?? track.artistName,
      imageUrl: existing?.imageUrl ?? track.imageUrl,
      score: (existing?.score ?? 0) + (n - index),
    })
  })
  return [...albums.values()].sort((a, b) => b.score - a.score)
}

interface Count {
  streams: number
  ms: number
}

/**
 * Groups streams (≥ 30 s) and ranks by streams, then listening time (the DAO's ORDER BY), one
 * play at a time. Only one play per group is kept in memory.
 */
export class StreamRanking<T> implements Accumulator<Ranked<T>[]> {
  readonly #groups = new Map<string, { first: PlayEvent; count: Count }>()

  constructor(
    private readonly group: (play: PlayEvent) => string | null,
    private readonly build: (first: PlayEvent, count: Count) => T,
    private readonly limit: number,
  ) {}

  add(play: PlayEvent) {
    if (play.msPlayed < STREAM_MS) return
    const key = this.group(play)
    if (key === null) return
    const entry = this.#groups.get(key) ?? { first: play, count: { streams: 0, ms: 0 } }
    entry.count.streams++
    entry.count.ms += play.msPlayed
    if (!entry.first.imageUrl && play.imageUrl) entry.first = { ...entry.first, imageUrl: play.imageUrl }
    this.#groups.set(key, entry)
  }

  result(): Ranked<T>[] {
    return [...this.#groups.entries()]
      .sort(
        ([keyA, a], [keyB, b]) =>
          b.count.streams - a.count.streams || b.count.ms - a.count.ms || (keyA < keyB ? -1 : 1),
      )
      .slice(0, this.limit)
      .map(([, { first, count }], index) => ({
        rank: index + 1,
        item: this.build(first, count),
        movement: { kind: 'unknown' } as const,
        streams: count.streams,
        listenedMs: count.ms,
      }))
  }
}

export const topTracksAccumulator = (limit = TOP_LIST_SIZE) =>
  new StreamRanking<TopTrack>(
    (play) => `${play.trackName}|${play.artistName}`,
    (play) => ({
      id: play.trackId,
      name: play.trackName,
      artistName: play.artistName,
      albumId: null,
      albumName: play.albumName,
      imageUrl: play.imageUrl,
    }),
    limit,
  )

export const topArtistsAccumulator = (limit = TOP_LIST_SIZE) =>
  new StreamRanking<TopArtist>(
    (play) => play.artistName,
    (play) => ({ id: null, name: play.artistName, imageUrl: play.imageUrl, genres: [] }),
    limit,
  )

export const topAlbumsAccumulator = (limit = TOP_LIST_SIZE) =>
  new StreamRanking<TopAlbum>(
    (play) => (play.albumName ? `${play.albumName}|${play.artistName}` : null),
    (play, count) => ({
      key: `${play.albumName ?? ''}|${play.artistName}`,
      name: play.albumName ?? '',
      artistName: play.artistName,
      imageUrl: play.imageUrl,
      score: count.streams,
    }),
    limit,
  )

const rankAll = <T>(plays: readonly PlayEvent[], accumulator: StreamRanking<T>) => {
  feed(plays, accumulator)
  return accumulator.result()
}

export const localTopTracks = (plays: readonly PlayEvent[], limit = TOP_LIST_SIZE) =>
  rankAll(plays, topTracksAccumulator(limit))
export const localTopArtists = (plays: readonly PlayEvent[], limit = TOP_LIST_SIZE) =>
  rankAll(plays, topArtistsAccumulator(limit))
export const localTopAlbums = (plays: readonly PlayEvent[], limit = TOP_LIST_SIZE) =>
  rankAll(plays, topAlbumsAccumulator(limit))

export interface TopResult<T> {
  readonly items: readonly Ranked<T>[]
  /** When the list was fetched from Spotify (null for local lists). */
  readonly fetchedAt: number | null
  /** Set when Spotify could not be reached; `items` then come from the cache, if any. */
  readonly error: AppError | null
}

/**
 * The Strategy of GetTop*UseCase: Spotify's ranges come from the API (cached, with movement),
 * the lifetime list from local history (synced and imported plays).
 */
export class GetTopItems {
  constructor(private readonly deps: { top: TopItemsRepository; history: HistoryRepository; now: () => number }) {}

  /** The all-time list, streamed over the whole history (never loaded into one array). */
  async #lifetime<T>(accumulator: StreamRanking<T>): Promise<TopResult<T>> {
    await this.deps.history.scan(0, this.deps.now() + 60_000, (play) => accumulator.add(play))
    return { items: accumulator.result(), fetchedAt: null, error: null }
  }

  async tracks(range: TimeRange, refresh = false): Promise<TopResult<TopTrack>> {
    if (!isApiBacked(range)) return this.#lifetime(topTracksAccumulator())
    const { list, error } = await this.deps.top.topTracks(range, { refresh })
    return { items: list ? rankList(list, trackKey) : [], fetchedAt: list?.fetchedAt ?? null, error }
  }

  async artists(range: TimeRange, refresh = false): Promise<TopResult<TopArtist>> {
    if (!isApiBacked(range)) return this.#lifetime(topArtistsAccumulator())
    const { list, error } = await this.deps.top.topArtists(range, { refresh })
    return { items: list ? rankList(list, artistKey) : [], fetchedAt: list?.fetchedAt ?? null, error }
  }

  /** Albums are derived: Borda count over top tracks (API ranges) or streams (lifetime). */
  async albums(range: TimeRange, refresh = false): Promise<TopResult<TopAlbum>> {
    if (!isApiBacked(range)) return this.#lifetime(topAlbumsAccumulator())
    const { list, error } = await this.deps.top.topTracks(range, { refresh })
    const items = albumsFromTracks(list?.items ?? []).map((item, index) => ({
      rank: index + 1,
      item,
      movement: { kind: 'unknown' } as const,
      streams: null,
      listenedMs: null,
    }))
    return { items, fetchedAt: list?.fetchedAt ?? null, error }
  }
}
