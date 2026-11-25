/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import Dexie from 'dexie'
import type { AppStateRepository, HistoryRepository } from '$lib/domain/repositories'
import { dedupeKeyOf } from '$lib/domain/stats'
import type { PlayEvent } from '$lib/model/music'
import type { MusicManiaDatabase, PlayRow } from './database'

/** Strips the storage-only fields. */
const toPlay = ({ id, dedupeKey, ...play }: PlayRow): PlayEvent => play

export class DexieHistoryRepository implements HistoryRepository {
  constructor(private readonly db: MusicManiaDatabase) {}

  async addPlays(plays: readonly PlayEvent[]): Promise<number> {
    if (plays.length === 0) return 0
    const rows: PlayRow[] = plays.map((play) => ({
      ...play,
      dedupeKey: dedupeKeyOf(play.trackName, play.artistName, play.playedAt),
    }))
    try {
      await this.db.plays.bulkAdd(rows)
      return rows.length
    } catch (error) {
      // Duplicates (already stored, or twice in this batch) are skipped; the rest is committed.
      if (error instanceof Dexie.BulkError) return rows.length - Object.keys(error.failuresByPos).length
      throw error
    }
  }

  async playsBetween(start: number, end: number): Promise<PlayEvent[]> {
    const rows = await this.db.plays.where('playedAt').between(start, end, true, false).toArray()
    return rows.map(toPlay)
  }

  async recent(limit: number, before?: number): Promise<PlayEvent[]> {
    const query =
      before === undefined
        ? this.db.plays.orderBy('playedAt').reverse()
        : this.db.plays.where('playedAt').below(before).reverse()
    return (await query.limit(limit).toArray()).map(toPlay)
  }

  async playTimes(): Promise<number[]> {
    return (await this.db.plays.orderBy('playedAt').keys()) as number[]
  }

  count(): Promise<number> {
    return this.db.plays.count()
  }
}

const CURSOR = 'sync.recentlyPlayedCursor'
const LAST_SYNC = 'sync.lastSuccess'

export class DexieAppStateRepository implements AppStateRepository {
  constructor(private readonly db: MusicManiaDatabase) {}

  async #number(key: string): Promise<number | null> {
    const value = (await this.db.state.get(key))?.value
    return typeof value === 'number' && Number.isFinite(value) ? value : null
  }

  recentlyPlayedCursor = () => this.#number(CURSOR)
  lastSync = () => this.#number(LAST_SYNC)

  async setRecentlyPlayedCursor(cursor: number): Promise<void> {
    await this.db.state.put({ key: CURSOR, value: cursor })
  }

  async setLastSync(at: number): Promise<void> {
    await this.db.state.put({ key: LAST_SYNC, value: at })
  }
}
