/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { AppStateRepository, HistoryRepository, SpotifyRemote } from '$lib/domain/repositories'
import { dedupeKeyOf } from '$lib/domain/stats'
import type { NowPlaying, PlayEvent, RecentlyPlayedPage, UserProfile } from '$lib/model/music'
import type { ApiTimeRange, TopArtist, TopTrack } from '$lib/model/top'
import { success, type DataResult } from '$lib/model/result'

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
