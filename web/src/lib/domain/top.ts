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
import { STREAM_MS } from './stats'

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

/** Groups streams (≥ 30 s) and ranks by streams, then listening time (the DAO's ORDER BY). */
function rankByStreams<T>(
  plays: readonly PlayEvent[],
  group: (play: PlayEvent) => string | null,
  build: (first: PlayEvent, count: Count) => T,
  limit: number,
): Ranked<T>[] {
  const groups = new Map<string, { first: PlayEvent; count: Count }>()
  for (const play of plays) {
    if (play.msPlayed < STREAM_MS) continue
    const key = group(play)
    if (key === null) continue
    const entry = groups.get(key) ?? { first: play, count: { streams: 0, ms: 0 } }
    entry.count.streams++
    entry.count.ms += play.msPlayed
    if (!entry.first.imageUrl && play.imageUrl) entry.first = { ...entry.first, imageUrl: play.imageUrl }
    groups.set(key, entry)
  }
  return [...groups.entries()]
    .sort(
      ([keyA, a], [keyB, b]) => b.count.streams - a.count.streams || b.count.ms - a.count.ms || (keyA < keyB ? -1 : 1),
    )
    .slice(0, limit)
    .map(([, { first, count }], index) => ({
      rank: index + 1,
      item: build(first, count),
      movement: { kind: 'unknown' } as const,
      streams: count.streams,
      listenedMs: count.ms,
    }))
}

export const localTopTracks = (plays: readonly PlayEvent[], limit = TOP_LIST_SIZE): Ranked<TopTrack>[] =>
  rankByStreams(
    plays,
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

export const localTopArtists = (plays: readonly PlayEvent[], limit = TOP_LIST_SIZE): Ranked<TopArtist>[] =>
  rankByStreams(
    plays,
    (play) => play.artistName,
    (play) => ({ id: null, name: play.artistName, imageUrl: play.imageUrl, genres: [] }),
    limit,
  )

export const localTopAlbums = (plays: readonly PlayEvent[], limit = TOP_LIST_SIZE): Ranked<TopAlbum>[] =>
  rankByStreams(
    plays,
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

  async #lifetime(): Promise<readonly PlayEvent[]> {
    return this.deps.history.playsBetween(0, this.deps.now() + 60_000)
  }

  async tracks(range: TimeRange, refresh = false): Promise<TopResult<TopTrack>> {
    if (!isApiBacked(range)) return { items: localTopTracks(await this.#lifetime()), fetchedAt: null, error: null }
    const { list, error } = await this.deps.top.topTracks(range, { refresh })
    return { items: list ? rankList(list, trackKey) : [], fetchedAt: list?.fetchedAt ?? null, error }
  }

  async artists(range: TimeRange, refresh = false): Promise<TopResult<TopArtist>> {
    if (!isApiBacked(range)) return { items: localTopArtists(await this.#lifetime()), fetchedAt: null, error: null }
    const { list, error } = await this.deps.top.topArtists(range, { refresh })
    return { items: list ? rankList(list, artistKey) : [], fetchedAt: list?.fetchedAt ?? null, error }
  }

  /** Albums are derived: Borda count over top tracks (API ranges) or streams (lifetime). */
  async albums(range: TimeRange, refresh = false): Promise<TopResult<TopAlbum>> {
    if (!isApiBacked(range)) return { items: localTopAlbums(await this.#lifetime()), fetchedAt: null, error: null }
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
