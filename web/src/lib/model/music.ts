/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

export type PlaySource = 'SYNC' | 'IMPORT'

/**
 * One play. Names are denormalised on purpose (the basic export has no Spotify IDs), exactly like
 * PlayEventEntity: aggregations group by names so synced and imported plays unify.
 */
export interface PlayEvent {
  readonly trackId: string | null
  readonly trackName: string
  readonly artistName: string
  readonly albumName: string | null
  readonly imageUrl: string | null
  /** Epoch millis of the moment the play ended (Spotify semantics). */
  readonly playedAt: number
  readonly msPlayed: number
  readonly source: PlaySource
  readonly skipped: boolean | null
  readonly shuffle: boolean | null
  readonly platform: string | null
}

export interface ListeningSummary {
  readonly listenedMs: number
  /** Plays of 30 s or more. */
  readonly streams: number
  readonly uniqueTracks: number
  readonly uniqueArtists: number
}

export interface Streaks {
  readonly current: number
  readonly longest: number
  /** Local date (YYYY-MM-DD) the longest streak ended on. */
  readonly longestEndedOn: string | null
}

export interface NowPlaying {
  readonly trackName: string
  readonly artistName: string
  readonly imageUrl: string | null
  readonly isPlaying: boolean
  readonly progressMs: number
  readonly durationMs: number
}

export interface UserProfile {
  readonly id: string
  readonly displayName: string | null
  readonly imageUrl: string | null
}

/** A page of Spotify's recently-played (at most 50 plays, the API's hard limit). */
export interface RecentlyPlayedPage {
  readonly plays: readonly PlayEvent[]
  /** Cursor for the next call (epoch millis), when Spotify returns one. */
  readonly nextAfter: number | null
}

export interface SyncStatus {
  readonly running: boolean
  readonly lastSuccess: number | null
  /** Spotify only keeps the last 50 plays: a full page since the last sync may hide older plays. */
  readonly possibleGap: boolean
}
