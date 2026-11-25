/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { ListeningSummary, PlayEvent, Streaks } from '$lib/model/music'
import { addDays, type LocalDate } from './dates'

/** A play counts as a stream from 30 seconds on (PlayEventDao.STREAM_MS). */
export const STREAM_MS = 30_000

/**
 * Same song by the same artist ending in the same second is the same play, so sync and import
 * never double count. Identical to PlayEventEntity.dedupeKeyOf in the Android app.
 */
export function dedupeKeyOf(trackName: string, artistName: string, playedAtMs: number): string {
  return `${trackName.trim().toLowerCase()}|${artistName.trim().toLowerCase()}|${Math.floor(playedAtMs / 1000)}`
}

/** The aggregates of PlayEventDao.observeSummary. */
export function summarize(plays: readonly PlayEvent[]): ListeningSummary {
  let listenedMs = 0
  let streams = 0
  const tracks = new Set<string>()
  const artists = new Set<string>()
  for (const play of plays) {
    listenedMs += play.msPlayed
    if (play.msPlayed >= STREAM_MS) streams++
    tracks.add(`${play.trackName}|${play.artistName}`)
    artists.add(play.artistName)
  }
  return { listenedMs, streams, uniqueTracks: tracks.size, uniqueArtists: artists.size }
}

/**
 * Port of StreakCalculator. A streak is still "current" if its last day is today or yesterday
 * (the user may not have listened yet today).
 */
export function computeStreaks(activeDays: Iterable<LocalDate>, today: LocalDate): Streaks {
  const days = [...new Set(activeDays)].sort()
  if (days.length === 0) return { current: 0, longest: 0, longestEndedOn: null }
  let longest = 0
  let longestEnd: LocalDate | null = null
  let run = 0
  let previous: LocalDate | null = null
  for (const day of days) {
    run = previous !== null && addDays(previous, 1) === day ? run + 1 : 1
    if (run >= longest) {
      longest = run
      longestEnd = day
    }
    previous = day
  }
  const last = days[days.length - 1]
  const current = last === today || last === addDays(today, -1) ? run : 0
  return { current, longest, longestEndedOn: longestEnd }
}

export interface ArtistCount {
  readonly artistName: string
  readonly streams: number
  readonly imageUrl: string | null
}

/** Most played artist by streams (ties: most listening time, then name), for the home screen. */
export function topArtist(plays: readonly PlayEvent[]): ArtistCount | null {
  const byArtist = new Map<string, { streams: number; ms: number; imageUrl: string | null }>()
  for (const play of plays) {
    if (play.msPlayed < STREAM_MS) continue
    const entry = byArtist.get(play.artistName) ?? { streams: 0, ms: 0, imageUrl: null }
    entry.streams++
    entry.ms += play.msPlayed
    entry.imageUrl ??= play.imageUrl
    byArtist.set(play.artistName, entry)
  }
  let best: ArtistCount | null = null
  let bestMs = 0
  for (const [artistName, entry] of byArtist) {
    const better =
      best === null ||
      entry.streams > best.streams ||
      (entry.streams === best.streams && (entry.ms > bestMs || (entry.ms === bestMs && artistName < best.artistName)))
    if (better) {
      best = { artistName, streams: entry.streams, imageUrl: entry.imageUrl }
      bestMs = entry.ms
    }
  }
  return best
}
