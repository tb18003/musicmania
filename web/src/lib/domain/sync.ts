/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { SyncStatus } from '$lib/model/music'
import { failure, success, type DataResult } from '$lib/model/result'
import type { AppStateRepository, HistoryRepository, SpotifyRemote } from './repositories'

/** Spotify's quota is shared by every user of the Client ID: never sync more often than this. */
export const MIN_SYNC_INTERVAL_MS = 5 * 60_000
/** recently-played never returns more than this many plays. */
export const RECENTLY_PLAYED_LIMIT = 50

export interface SyncOutcome {
  readonly skipped: boolean
  readonly inserted: number
  readonly possibleGap: boolean
}

type Listener = (status: SyncStatus) => void

/**
 * Accumulates Spotify's last 50 plays into local history, like SyncWorker on Android. iOS gives
 * web apps no background work, so this runs when the app opens and when it comes back to the
 * foreground; throttled, single-flight, and observable by the screens.
 */
export class HistorySync {
  #status: SyncStatus = { running: false, lastSuccess: null, possibleGap: false }
  #inFlight: Promise<DataResult<SyncOutcome>> | null = null
  readonly #listeners = new Set<Listener>()

  constructor(
    private readonly deps: {
      remote: SpotifyRemote
      history: HistoryRepository
      appState: AppStateRepository
      now: () => number
    },
  ) {}

  get status(): SyncStatus {
    return this.#status
  }

  subscribe(listener: Listener): () => void {
    this.#listeners.add(listener)
    listener(this.#status)
    return () => this.#listeners.delete(listener)
  }

  /** Loads the last successful sync time so screens can show it before the first sync. */
  async restore(): Promise<void> {
    this.#emit({ ...this.#status, lastSuccess: await this.deps.appState.lastSync() })
  }

  /** Syncs unless one ran less than MIN_SYNC_INTERVAL_MS ago (`force` skips the throttle). */
  run(options: { force?: boolean } = {}): Promise<DataResult<SyncOutcome>> {
    this.#inFlight ??= this.#run(options.force ?? false).finally(() => {
      this.#inFlight = null
    })
    return this.#inFlight
  }

  async #run(force: boolean): Promise<DataResult<SyncOutcome>> {
    const { remote, history, appState, now } = this.deps
    const last = await appState.lastSync()
    if (!force && last !== null && now() - last < MIN_SYNC_INTERVAL_MS) {
      return success({ skipped: true, inserted: 0, possibleGap: this.#status.possibleGap })
    }
    this.#emit({ ...this.#status, running: true })
    const cursor = await appState.recentlyPlayedCursor()
    const page = await remote.recentlyPlayed(cursor)
    if (!page.ok) {
      this.#emit({ ...this.#status, running: false })
      return failure(page.error)
    }
    const inserted = await history.addPlays(page.data.plays)
    const newest = page.data.plays.reduce((max, play) => Math.max(max, play.playedAt), 0)
    const nextCursor = page.data.nextAfter ?? (newest > 0 ? newest : null)
    if (nextCursor !== null) await appState.setRecentlyPlayedCursor(nextCursor)
    const at = now()
    await appState.setLastSync(at)
    // A full page after a known cursor means more than 50 plays happened since the last sync.
    const possibleGap = cursor !== null && page.data.plays.length >= RECENTLY_PLAYED_LIMIT
    this.#emit({ running: false, lastSuccess: at, possibleGap })
    return success({ skipped: false, inserted, possibleGap })
  }

  #emit(status: SyncStatus) {
    this.#status = status
    for (const listener of this.#listeners) listener(status)
  }
}
