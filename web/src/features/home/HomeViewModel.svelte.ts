/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { dayRange, localDateOf, weekRange } from '$lib/domain/dates'
import type { HistoryRepository, SpotifyRemote } from '$lib/domain/repositories'
import { computeStreaks, summarize, topArtist, type ArtistCount } from '$lib/domain/stats'
import type { HistorySync } from '$lib/domain/sync'
import type { ListeningSummary, NowPlaying, Streaks, SyncStatus, UserProfile } from '$lib/model/music'
import type { AppError } from '$lib/model/result'

export interface HomeState {
  loading: boolean
  profile: UserProfile | null
  today: ListeningSummary
  week: ListeningSummary
  streaks: Streaks
  topArtist: ArtistCount | null
  nowPlaying: NowPlaying | null
  totalPlays: number
  sync: SyncStatus
  error: AppError | null
}

export interface HomeDependencies {
  history: HistoryRepository
  remote: SpotifyRemote
  sync: HistorySync
  /** Runs a sync through the app (it handles an expired session). */
  runSync: (force: boolean) => Promise<AppError | null>
  now: () => number
}

const EMPTY: ListeningSummary = { listenedMs: 0, streams: 0, uniqueTracks: 0, uniqueArtists: 0 }

export class HomeViewModel {
  state = $state<HomeState>({
    loading: true,
    profile: null,
    today: EMPTY,
    week: EMPTY,
    streaks: { current: 0, longest: 0, longestEndedOn: null },
    topArtist: null,
    nowPlaying: null,
    totalPlays: 0,
    sync: { running: false, lastSuccess: null, possibleGap: false },
    error: null,
  })

  readonly #deps: HomeDependencies

  constructor(deps: HomeDependencies) {
    this.#deps = deps
  }

  /** Starts observing; call the returned function to stop (like collectAsStateWithLifecycle). */
  start(): () => void {
    let lastSuccess: number | null = null
    const unsubscribe = this.#deps.sync.subscribe((status) => {
      this.state.sync = status
      // Fresh plays arrived: recompute the numbers.
      if (status.lastSuccess !== lastSuccess) {
        lastSuccess = status.lastSuccess
        void this.load()
      }
    })
    void this.load()
    void this.#loadRemote()
    return unsubscribe
  }

  async load(): Promise<void> {
    const { history, now } = this.#deps
    const today = localDateOf(now())
    const day = dayRange(today)
    const week = weekRange(today)
    const [todayPlays, weekPlays, times, total] = await Promise.all([
      history.playsBetween(day.start, day.end),
      history.playsBetween(week.start, week.end),
      history.playTimes(),
      history.count(),
    ])
    this.state.today = summarize(todayPlays)
    this.state.week = summarize(weekPlays)
    this.state.topArtist = topArtist(weekPlays)
    this.state.streaks = computeStreaks(times.map(localDateOf), today)
    this.state.totalPlays = total
    this.state.loading = false
  }

  async #loadRemote(): Promise<void> {
    const [profile, playing] = await Promise.all([this.#deps.remote.me(), this.#deps.remote.nowPlaying()])
    if (profile.ok) this.state.profile = profile.data
    if (playing.ok) this.state.nowPlaying = playing.data
  }

  async syncNow(): Promise<void> {
    this.state.error = await this.#deps.runSync(true)
    void this.#loadRemote()
  }

  /** Errors from the automatic sync on open/foreground, shown on this screen. */
  reportSyncError(error: AppError | null) {
    this.state.error = error
  }
}
