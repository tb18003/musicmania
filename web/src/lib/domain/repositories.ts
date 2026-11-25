/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type {
  CloudProfile,
  FriendStats,
  LeaderboardEntry,
  LeaderboardMetric,
  LeaderboardPeriod,
  SharedPeriodStats,
} from '$lib/model/leaderboard'
import type { NowPlaying, PlayEvent, RecentlyPlayedPage, UserProfile } from '$lib/model/music'
import type { AppError, DataResult } from '$lib/model/result'
import type { ApiTimeRange, ImportProgress, TopArtist, TopList, TopTrack } from '$lib/model/top'

/** Interfaces live in the domain; implementations in data (dependency inversion, like core:domain). */

export interface HistoryRepository {
  /** Inserts plays, ignoring ones already stored (same dedupe key). Returns how many were new. */
  addPlays(plays: readonly PlayEvent[]): Promise<number>
  /** Plays with start <= playedAt < end, oldest first. */
  playsBetween(start: number, end: number): Promise<PlayEvent[]>
  /** The newest plays, newest first, strictly before `before` when given (for paging). */
  recent(limit: number, before?: number): Promise<PlayEvent[]>
  /** Every play time (epoch millis), cheap: read from the index only. */
  playTimes(): Promise<number[]>
  count(): Promise<number>
}

export interface AppStateRepository {
  recentlyPlayedCursor(): Promise<number | null>
  setRecentlyPlayedCursor(cursor: number): Promise<void>
  lastSync(): Promise<number | null>
  setLastSync(at: number): Promise<void>
}

/** Spotify Web API, as the sync and the screens need it. */
export interface SpotifyRemote {
  me(): Promise<DataResult<UserProfile>>
  recentlyPlayed(after: number | null): Promise<DataResult<RecentlyPlayedPage>>
  nowPlaying(): Promise<DataResult<NowPlaying | null>>
  topTracks(range: ApiTimeRange): Promise<DataResult<TopTrack[]>>
  topArtists(range: ApiTimeRange): Promise<DataResult<TopArtist[]>>
}

/** What a cached read returns: the best list we have, and why it may be old. */
export interface Cached<T> {
  readonly list: TopList<T> | null
  readonly error: AppError | null
}

/** Spotify's top lists, cached locally with daily snapshots (offline first, ▲▼ NEW). */
export interface TopItemsRepository {
  topTracks(range: ApiTimeRange, options?: { refresh?: boolean }): Promise<Cached<TopTrack>>
  topArtists(range: ApiTimeRange, options?: { refresh?: boolean }): Promise<Cached<TopArtist>>
}

/** A user-picked file (a Blob with a name): the Spotify data export, ZIP or JSON. */
export interface ImportFile {
  readonly name: string
  readonly size: number
  stream(): ReadableStream<Uint8Array>
  text(): Promise<string>
}

export interface StreamingHistoryImporter {
  import(
    files: readonly ImportFile[],
    onProgress: (progress: ImportProgress) => void,
  ): Promise<DataResult<ImportProgress>>
}

export interface AuthRepository {
  readonly configured: boolean
  /** Prepares PKCE and returns the Spotify authorization URL to navigate to. */
  beginLogin(): Promise<string>
  /** Handles the redirect back from Spotify (validates state, exchanges the code). */
  completeLogin(callbackUrl: string): Promise<DataResult<void>>
  isSignedIn(): Promise<boolean>
}

/** The MusicMania API (friends ranking). Disabled, and never touching the network, without a URL. */
export interface CloudRepository {
  readonly enabled: boolean
  me(): Promise<DataResult<CloudProfile>>
  setSharing(enabled: boolean): Promise<DataResult<CloudProfile>>
  publish(stats: readonly SharedPeriodStats[]): Promise<DataResult<void>>
  leaderboard(
    metric: LeaderboardMetric,
    period: LeaderboardPeriod,
    start: string,
  ): Promise<DataResult<LeaderboardEntry[]>>
  friendStats(spotifyUserId: string, period: LeaderboardPeriod, start: string): Promise<DataResult<FriendStats>>
  deleteMyData(): Promise<DataResult<void>>
}

export interface SessionRepository {
  /** Wipes tokens, the encryption key and every bit of local data (security rule 9). */
  signOut(): Promise<void>
}
