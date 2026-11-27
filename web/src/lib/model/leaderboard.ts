/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** Same values as the API (and LeaderboardMetric / LeaderboardPeriod on Android). */
export type LeaderboardMetric = 'minutes' | 'streams' | 'streak' | 'unique_artists'
export type LeaderboardPeriod = 'week' | 'month' | 'year' | 'all_time'
export const LEADERBOARD_PERIODS: readonly LeaderboardPeriod[] = ['week', 'month', 'year', 'all_time']

export interface LeaderboardEntry {
  readonly rank: number
  readonly spotifyUserId: string
  readonly displayName: string | null
  readonly avatarUrl: string | null
  readonly value: number
  readonly isMe: boolean
}

export interface CloudProfile {
  readonly spotifyUserId: string
  readonly displayName: string | null
  readonly sharingEnabled: boolean
}

/** One entry of a top-10 list: the name and its public cover (Spotify CDN), if known. */
export interface RankedItem {
  readonly name: string
  readonly imageUrl: string | null
}

/** The only data that ever leaves the device, and only after opting in (security rule 10). */
export interface SharedPeriodStats {
  readonly period: LeaderboardPeriod
  /** First day of the period (YYYY-MM-DD); 1970-01-01 for all time. */
  readonly start: string
  readonly minutes: number
  readonly streams: number
  readonly uniqueArtists: number
  readonly uniqueTracks: number
  readonly currentStreak: number
  readonly longestStreak: number
  readonly topArtists: readonly RankedItem[]
  readonly topTracks: readonly RankedItem[]
}

export interface FriendStats {
  readonly spotifyUserId: string
  readonly displayName: string | null
  readonly minutes: number
  readonly streams: number
  readonly uniqueArtists: number
  readonly currentStreak: number
  readonly topArtists: readonly RankedItem[]
  readonly topTracks: readonly RankedItem[]
}
