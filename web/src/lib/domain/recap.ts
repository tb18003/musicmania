/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { ListeningSummary } from '$lib/model/music'
import type { Ranked, TopAlbum, TopArtist, TopTrack } from '$lib/model/top'
import { localDateOf, type DateRange } from './dates'
import {
  NewArtistsAccumulator,
  PatternAccumulator,
  peakHour,
  personalityFromPeakHour,
  type ListeningClock,
  type Personality,
} from './insights'
import type { HistoryRepository } from './repositories'
import { computeStreaks, SummaryAccumulator } from './stats'
import { topAlbumsAccumulator, topArtistsAccumulator, topTracksAccumulator } from './top'

export type RecapPeriod = 'LAST_30_DAYS' | 'THIS_MONTH' | 'LAST_MONTH' | 'THIS_YEAR' | 'LAST_YEAR'
export const RECAP_PERIODS: readonly RecapPeriod[] = [
  'LAST_30_DAYS',
  'THIS_MONTH',
  'LAST_MONTH',
  'THIS_YEAR',
  'LAST_YEAR',
]

const RECAP_TOP = 5
const DAY_MS = 24 * 60 * 60_000

/** RecapViewModel.rangeFor: a rolling 30 days, or calendar months/years in local time. */
export function recapRange(period: RecapPeriod, now: number): DateRange {
  const today = new Date(now)
  const year = today.getFullYear()
  const month = today.getMonth()
  switch (period) {
    case 'LAST_30_DAYS':
      return { start: now - 30 * DAY_MS, end: now + 1_000 }
    case 'THIS_MONTH':
      return { start: new Date(year, month, 1).getTime(), end: new Date(year, month + 1, 1).getTime() }
    case 'LAST_MONTH':
      return { start: new Date(year, month - 1, 1).getTime(), end: new Date(year, month, 1).getTime() }
    case 'THIS_YEAR':
      return { start: new Date(year, 0, 1).getTime(), end: new Date(year + 1, 0, 1).getTime() }
    case 'LAST_YEAR':
      return { start: new Date(year - 1, 0, 1).getTime(), end: new Date(year, 0, 1).getTime() }
  }
}

/** Everything a story-style recap needs for a window (Recap on Android). */
export interface Recap {
  readonly range: DateRange
  readonly summary: ListeningSummary
  readonly topArtists: readonly Ranked<TopArtist>[]
  readonly topTracks: readonly Ranked<TopTrack>[]
  readonly topAlbum: Ranked<TopAlbum> | null
  readonly clock: ListeningClock
  readonly personality: Personality | null
  readonly longestStreak: number
  readonly newArtists: number
}

export const isEmptyRecap = (recap: Recap) => recap.summary.streams === 0

/** Port of BuildRecapUseCase, in one streamed pass up to the end of the window. */
export async function buildRecap(history: HistoryRepository, range: DateRange, now: number): Promise<Recap> {
  const summary = new SummaryAccumulator()
  const artists = topArtistsAccumulator(RECAP_TOP)
  const tracks = topTracksAccumulator(RECAP_TOP)
  const albums = topAlbumsAccumulator(1)
  const patterns = new PatternAccumulator()
  const newArtists = new NewArtistsAccumulator(range)
  const days = new Set<string>()
  // Earlier plays matter too: an artist is "new" only if never heard before the window.
  await history.scan(0, range.end, (play) => {
    newArtists.add(play)
    if (play.playedAt < range.start) return
    summary.add(play)
    artists.add(play)
    tracks.add(play)
    albums.add(play)
    patterns.add(play)
    days.add(localDateOf(play.playedAt))
  })
  const clock = patterns.result().clock
  return {
    range,
    summary: summary.result(),
    topArtists: artists.result(),
    topTracks: tracks.result(),
    topAlbum: albums.result()[0] ?? null,
    clock,
    personality: personalityFromPeakHour(peakHour(clock)),
    longestStreak: computeStreaks(days, localDateOf(now)).longest,
    newArtists: newArtists.result(),
  }
}

export type StoryPage = 'INTRO' | 'MINUTES' | 'TOP_ARTIST' | 'TOP_TRACKS' | 'PERSONALITY' | 'DISCOVERIES' | 'SHARE'

/** StoryPages.buildPages: pages without data are skipped. */
export function buildPages(recap: Recap): StoryPage[] {
  const pages: StoryPage[] = ['INTRO', 'MINUTES']
  if (recap.topArtists.length > 0) pages.push('TOP_ARTIST')
  if (recap.topTracks.length > 0) pages.push('TOP_TRACKS')
  if (recap.personality !== null) pages.push('PERSONALITY')
  pages.push('DISCOVERIES', 'SHARE')
  return pages
}
