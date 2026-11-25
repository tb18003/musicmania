/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Same ranges as TimeRange on Android: the first three come from Spotify's /me/top
 * (short_term, medium_term, long_term); LIFETIME is computed from local history.
 */
export type TimeRange = 'FOUR_WEEKS' | 'SIX_MONTHS' | 'ONE_YEAR' | 'LIFETIME'
export const TIME_RANGES: readonly TimeRange[] = ['FOUR_WEEKS', 'SIX_MONTHS', 'ONE_YEAR', 'LIFETIME']
export type ApiTimeRange = Exclude<TimeRange, 'LIFETIME'>
export const isApiBacked = (range: TimeRange): range is ApiTimeRange => range !== 'LIFETIME'

export type TopKind = 'tracks' | 'artists' | 'albums'

export type RankMovement =
  | { kind: 'unknown' }
  | { kind: 'new' }
  | { kind: 'same' }
  | { kind: 'up'; places: number }
  | { kind: 'down'; places: number }

export interface TopTrack {
  readonly id: string | null
  readonly name: string
  readonly artistName: string
  readonly albumId: string | null
  readonly albumName: string | null
  readonly imageUrl: string | null
}

export interface TopArtist {
  readonly id: string | null
  readonly name: string
  readonly imageUrl: string | null
  readonly genres: readonly string[]
}

export interface TopAlbum {
  readonly key: string
  readonly name: string
  readonly artistName: string
  readonly imageUrl: string | null
  /** Borda score (API ranges) or streams (lifetime). */
  readonly score: number
}

export interface Ranked<T> {
  readonly rank: number
  readonly item: T
  readonly movement: RankMovement
  /** Only for lists computed from local history. */
  readonly streams: number | null
  readonly listenedMs: number | null
}

/** A list from Spotify plus the ranks of the previous daily snapshot (for ▲▼ NEW). */
export interface TopList<T> {
  readonly items: readonly T[]
  readonly previousRanks: ReadonlyMap<string, number> | null
  readonly fetchedAt: number
}

export interface ImportProgress {
  readonly files: number
  readonly read: number
  readonly inserted: number
  readonly skipped: number
  readonly earliest: number | null
  readonly latest: number | null
}
