/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { NowPlaying, PlayEvent, RecentlyPlayedPage, UserProfile } from '$lib/model/music'
import type { DataResult } from '$lib/model/result'

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
}

export interface AuthRepository {
  readonly configured: boolean
  /** Prepares PKCE and returns the Spotify authorization URL to navigate to. */
  beginLogin(): Promise<string>
  /** Handles the redirect back from Spotify (validates state, exchanges the code). */
  completeLogin(callbackUrl: string): Promise<DataResult<void>>
  isSignedIn(): Promise<boolean>
}

export interface SessionRepository {
  /** Wipes tokens, the encryption key and every bit of local data (security rule 9). */
  signOut(): Promise<void>
}
