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

/** Something fed one play at a time (from `HistoryRepository.scan`), then asked for its result. */
export interface Accumulator<T> {
  add(play: PlayEvent): void
  result(): T
}

/** The aggregates of PlayEventDao.observeSummary, one play at a time. */
export class SummaryAccumulator implements Accumulator<ListeningSummary> {
  #listenedMs = 0
  #streams = 0
  readonly #tracks = new Set<string>()
  readonly #artists = new Set<string>()

  add(play: PlayEvent) {
    this.#listenedMs += play.msPlayed
    if (play.msPlayed >= STREAM_MS) this.#streams++
    this.#tracks.add(`${play.trackName}|${play.artistName}`)
    this.#artists.add(play.artistName)
  }

  result(): ListeningSummary {
    return {
      listenedMs: this.#listenedMs,
      streams: this.#streams,
      uniqueTracks: this.#tracks.size,
      uniqueArtists: this.#artists.size,
    }
  }
}

/** Feeds every play to every accumulator (handy for arrays already in memory). */
export function feed(plays: readonly PlayEvent[], ...accumulators: Accumulator<unknown>[]) {
  for (const play of plays) for (const accumulator of accumulators) accumulator.add(play)
}

export function summarize(plays: readonly PlayEvent[]): ListeningSummary {
  const summary = new SummaryAccumulator()
  feed(plays, summary)
  return summary.result()
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
