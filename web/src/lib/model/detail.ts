/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** What a detail screen shows, like DetailDestination on Android. */
export type DetailKind = 'track' | 'artist' | 'album'

export interface DetailTarget {
  readonly kind: DetailKind
  readonly name: string
  /** The artist of a track or album; empty for an artist. */
  readonly artist: string
  readonly id: string | null
  readonly imageUrl: string | null
}

/** Your own numbers for one track, artist or album (ItemStats). */
export interface ItemStats {
  readonly streams: number
  readonly listenedMs: number
  readonly firstPlayed: number | null
  readonly lastPlayed: number | null
  /** Streams per month ("YYYY-MM"), oldest first, months without plays included. */
  readonly monthly: readonly { month: string; streams: number }[]
}

export interface ArtistInfo {
  readonly genres: readonly string[]
  readonly imageUrl: string | null
}
