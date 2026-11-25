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

/** Rows per batch read in `scan`. */
export const SCAN_PAGE = 5_000

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

  /**
   * Reads the range in pages of SCAN_PAGE rows through the playedAt index: memory stays bounded and
   * each page is one fast batch read (a cursor costs a round trip per row, slow on Safari). Rows
   * sharing the boundary timestamp are tracked by id so none is visited twice or skipped.
   */
  async scan(start: number, end: number, visit: (play: PlayEvent) => void): Promise<void> {
    let from = start
    let seenAtFrom = new Set<number>()
    for (;;) {
      const limit = SCAN_PAGE + seenAtFrom.size
      const rows = await this.db.plays.where('playedAt').between(from, end, true, false).limit(limit).toArray()
      for (const row of rows) {
        if (row.playedAt === from && row.id !== undefined && seenAtFrom.has(row.id)) continue
        visit(toPlay(row))
      }
      const last = rows.at(-1)
      if (rows.length < limit || !last) return
      if (last.playedAt !== from) seenAtFrom = new Set()
      for (const row of rows) if (row.playedAt === last.playedAt && row.id !== undefined) seenAtFrom.add(row.id)
      from = last.playedAt
    }
  }

  async recent(limit: number, before?: number): Promise<PlayEvent[]> {
    const query =
      before === undefined
        ? this.db.plays.orderBy('playedAt').reverse()
        : this.db.plays.where('playedAt').below(before).reverse()
    return (await query.limit(limit).toArray()).map(toPlay)
  }

  /**
   * Batch reads through `scan`, never an index cursor: WebKit advances cursors one IPC round trip
   * per key, which stalled `orderBy('playedAt').keys()` on an imported history (seen in e2e).
   */
  async playTimes(): Promise<number[]> {
    const times: number[] = []
    await this.scan(0, Number.MAX_SAFE_INTEGER, (play) => times.push(play.playedAt))
    return times
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
