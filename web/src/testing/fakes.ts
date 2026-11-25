/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { AppStateRepository, CloudRepository, HistoryRepository, SpotifyRemote } from '$lib/domain/repositories'
import type {
  CloudProfile,
  FriendStats,
  LeaderboardEntry,
  LeaderboardMetric,
  LeaderboardPeriod,
  SharedPeriodStats,
} from '$lib/model/leaderboard'
import { dedupeKeyOf } from '$lib/domain/stats'
import type { NowPlaying, PlayEvent, RecentlyPlayedPage, UserProfile } from '$lib/model/music'
import type { ApiTimeRange, TopArtist, TopTrack } from '$lib/model/top'
import { success, type AppError, type DataResult } from '$lib/model/result'

/** Behavioural in-memory history (dedupes like the real one), like InMemoryHistoryRepository. */
export class InMemoryHistoryRepository implements HistoryRepository {
  readonly #plays = new Map<string, PlayEvent>()

  get plays(): PlayEvent[] {
    return [...this.#plays.values()].sort((a, b) => a.playedAt - b.playedAt)
  }

  async addPlays(plays: readonly PlayEvent[]): Promise<number> {
    let inserted = 0
    for (const play of plays) {
      const key = dedupeKeyOf(play.trackName, play.artistName, play.playedAt)
      if (this.#plays.has(key)) continue
      this.#plays.set(key, play)
      inserted++
    }
    return inserted
  }

  async playsBetween(start: number, end: number): Promise<PlayEvent[]> {
    return this.plays.filter((play) => play.playedAt >= start && play.playedAt < end)
  }

  async recent(limit: number, before?: number): Promise<PlayEvent[]> {
    return this.plays
      .filter((play) => before === undefined || play.playedAt < before)
      .reverse()
      .slice(0, limit)
  }

  async playTimes(): Promise<number[]> {
    return this.plays.map((play) => play.playedAt)
  }

  async count(): Promise<number> {
    return this.#plays.size
  }
}

export class InMemoryAppState implements AppStateRepository {
  cursor: number | null = null
  last: number | null = null
  recentlyPlayedCursor = async () => this.cursor
  setRecentlyPlayedCursor = async (cursor: number) => void (this.cursor = cursor)
  lastSync = async () => this.last
  setLastSync = async (at: number) => void (this.last = at)
}

/** Scripted Spotify: queue recently-played pages; profile and now playing are settable. */
export class FakeSpotifyRemote implements SpotifyRemote {
  readonly pages: DataResult<RecentlyPlayedPage>[] = []
  readonly calls: (number | null)[] = []
  profile: DataResult<UserProfile> = success({ id: 'me', displayName: 'Ana', imageUrl: null })
  playing: DataResult<NowPlaying | null> = success(null)

  /** Queued answers per range for /me/top (an empty list once the queue is exhausted). */
  readonly tracks: Partial<Record<ApiTimeRange, DataResult<TopTrack[]>[]>> = {}
  readonly artists: Partial<Record<ApiTimeRange, DataResult<TopArtist[]>[]>> = {}
  readonly topCalls: string[] = []

  me = async () => this.profile
  nowPlaying = async () => this.playing

  async topTracks(range: ApiTimeRange): Promise<DataResult<TopTrack[]>> {
    this.topCalls.push(`tracks:${range}`)
    return this.tracks[range]?.shift() ?? success([])
  }

  async topArtists(range: ApiTimeRange): Promise<DataResult<TopArtist[]>> {
    this.topCalls.push(`artists:${range}`)
    return this.artists[range]?.shift() ?? success([])
  }

  async recentlyPlayed(after: number | null): Promise<DataResult<RecentlyPlayedPage>> {
    this.calls.push(after)
    return this.pages.shift() ?? success({ plays: [], nextAfter: null })
  }
}

/** In-memory MusicMania API for one user, recording what was sent (like FakeCloudRepository). */
export class FakeCloudRepository implements CloudRepository {
  enabled = true
  profile: CloudProfile = { spotifyUserId: 'me', displayName: 'Me', sharingEnabled: false }
  /** When set, every call fails with it (e.g. notAllowlisted). */
  failure: AppError | null = null
  /** When set, only uploads fail. */
  publishFailure: AppError | null = null
  entries: LeaderboardEntry[] = []
  friends = new Map<string, FriendStats>()
  readonly published: (readonly SharedPeriodStats[])[] = []
  readonly leaderboardRequests: [LeaderboardMetric, LeaderboardPeriod, string][] = []
  deleted = false

  #respond<T>(value: () => T): DataResult<T> {
    return this.failure ? { ok: false, error: this.failure } : success(value())
  }

  me = async () => this.#respond(() => this.profile)

  setSharing = async (enabled: boolean) =>
    this.#respond(() => {
      this.profile = { ...this.profile, sharingEnabled: enabled }
      return this.profile
    })

  publish = async (stats: readonly SharedPeriodStats[]): Promise<DataResult<void>> =>
    this.publishFailure
      ? { ok: false, error: this.publishFailure }
      : this.#respond(() => void this.published.push(stats))

  leaderboard = async (metric: LeaderboardMetric, period: LeaderboardPeriod, start: string) =>
    this.#respond(() => {
      this.leaderboardRequests.push([metric, period, start])
      return this.entries
    })

  friendStats = async (id: string): Promise<DataResult<FriendStats>> => {
    if (this.failure) return { ok: false, error: this.failure }
    const stats = this.friends.get(id)
    return stats ? success(stats) : { ok: false, error: { kind: 'notFound' } }
  }

  deleteMyData = async () =>
    this.#respond(() => {
      this.deleted = true
      this.profile = { ...this.profile, sharingEnabled: false }
    })
}
