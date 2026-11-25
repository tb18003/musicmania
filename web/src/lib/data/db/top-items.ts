/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { localDateOf } from '$lib/domain/dates'
import type { Cached, SpotifyRemote, TopItemsRepository } from '$lib/domain/repositories'
import { artistKey, trackKey } from '$lib/domain/top'
import type { DataResult } from '$lib/model/result'
import type { ApiTimeRange, TopArtist, TopList, TopTrack } from '$lib/model/top'
import type { MusicManiaDatabase, SnapshotRow } from './database'

/** Spotify's top lists change slowly and the quota is shared: refetch at most this often. */
export const TOP_CACHE_TTL_MS = 6 * 60 * 60_000

/**
 * Offline-first top lists (like OfflineFirstTopItemsRepository): today's snapshot is served from
 * IndexedDB while fresh; otherwise Spotify is asked and the answer stored as today's snapshot.
 * The previous day's snapshot gives the ranks for ▲▼ NEW. If Spotify fails, the newest cached
 * list is returned with the error.
 */
export class CachedTopItemsRepository implements TopItemsRepository {
  constructor(
    private readonly db: MusicManiaDatabase,
    private readonly remote: SpotifyRemote,
    private readonly now: () => number,
  ) {}

  topTracks(range: ApiTimeRange, options: { refresh?: boolean } = {}): Promise<Cached<TopTrack>> {
    return this.#read('tracks', range, options.refresh ?? false, () => this.remote.topTracks(range), trackKey)
  }

  topArtists(range: ApiTimeRange, options: { refresh?: boolean } = {}): Promise<Cached<TopArtist>> {
    return this.#read('artists', range, options.refresh ?? false, () => this.remote.topArtists(range), artistKey)
  }

  async #read<T>(
    kind: string,
    range: ApiTimeRange,
    refresh: boolean,
    fetch: () => Promise<DataResult<T[]>>,
    key: (item: T) => string,
  ): Promise<Cached<T>> {
    const prefix = `${kind}:${range}:`
    const today = localDateOf(this.now())
    const todayId = prefix + today
    const latest = await this.#latest(prefix, '￿')
    const fresh = latest?.id === todayId && this.now() - latest.fetchedAt < TOP_CACHE_TTL_MS
    if (fresh && !refresh) return { list: await this.#withPrevious(latest, prefix, todayId, key), error: null }

    const result = await fetch()
    if (!result.ok) {
      return { list: latest ? await this.#withPrevious(latest, prefix, latest.id, key) : null, error: result.error }
    }
    const row: SnapshotRow = { id: todayId, fetchedAt: this.now(), items: result.data }
    await this.db.snapshots.put(row)
    return { list: await this.#withPrevious(row, prefix, todayId, key), error: null }
  }

  /** The newest snapshot with an id strictly below `before` (within the prefix). */
  async #latest(prefix: string, before: string): Promise<SnapshotRow | undefined> {
    return this.db.snapshots
      .where('id')
      .between(prefix, prefix + before, true, false)
      .last()
  }

  async #withPrevious<T>(
    row: SnapshotRow,
    prefix: string,
    currentId: string,
    key: (item: T) => string,
  ): Promise<TopList<T>> {
    const previous = await this.#latest(prefix, currentId.slice(prefix.length))
    const previousRanks = previous
      ? new Map((previous.items as T[]).map((item, index) => [key(item), index + 1] as const))
      : null
    return { items: row.items as T[], previousRanks, fetchedAt: row.fetchedAt }
  }
}
