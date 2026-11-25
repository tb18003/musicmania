/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import {
  LEADERBOARD_PERIODS,
  type FriendStats,
  type LeaderboardEntry,
  type LeaderboardMetric,
  type LeaderboardPeriod,
  type SharedPeriodStats,
} from '$lib/model/leaderboard'
import { success, type DataResult } from '$lib/model/result'
import { addDays, localDateOf, startOfDay, weekStart, type LocalDate } from './dates'
import type { CloudRepository, HistoryRepository } from './repositories'
import { computeStreaks, SummaryAccumulator } from './stats'
import { topArtistsAccumulator, topTracksAccumulator } from './top'

export const ALL_TIME_START: LocalDate = '1970-01-01'
const TOP_ITEMS = 10
const MAX_NAME = 200
const MINUTES_PER_DAY = 24 * 60

/** First day of the period containing `today` (weeks start on Monday), like LeaderboardPeriod.startOf. */
export function periodStart(period: LeaderboardPeriod, today: LocalDate): LocalDate {
  switch (period) {
    case 'week':
      return weekStart(today)
    case 'month':
      return `${today.slice(0, 7)}-01`
    case 'year':
      return `${today.slice(0, 4)}-01-01`
    case 'all_time':
      return ALL_TIME_START
  }
}

/** First day after the period that starts on `start`; null for all time. */
export function periodEnd(period: LeaderboardPeriod, start: LocalDate): LocalDate | null {
  switch (period) {
    case 'week':
      return addDays(start, 7)
    case 'month': {
      const [year, month] = start.split('-').map(Number) as [number, number]
      return localDateOf(new Date(year, month, 1).getTime())
    }
    case 'year':
      return `${Number(start.slice(0, 4)) + 1}-01-01`
    case 'all_time':
      return null
  }
}

/** The same caps as the API: a period cannot hold more minutes than it has. */
const MAX_MINUTES: Record<LeaderboardPeriod, number> = {
  week: 7 * MINUTES_PER_DAY,
  month: 31 * MINUTES_PER_DAY,
  year: 366 * MINUTES_PER_DAY,
  all_time: 100 * 366 * MINUTES_PER_DAY,
}

/**
 * Port of BuildSharedStatsUseCase: one aggregate per calendar period, nothing else. A single
 * streamed pass over the history feeds every period whose window contains the play.
 */
export async function buildSharedStats(history: HistoryRepository, now: number): Promise<SharedPeriodStats[]> {
  const today = localDateOf(now)
  const times = await history.playTimes()
  const streaks = computeStreaks(times.map(localDateOf), today)
  const periods = LEADERBOARD_PERIODS.map((period) => {
    const start = periodStart(period, today)
    const end = periodEnd(period, start)
    return {
      period,
      start,
      from: startOfDay(start),
      until: end ? startOfDay(end) : now + 60_000,
      summary: new SummaryAccumulator(),
      artists: topArtistsAccumulator(TOP_ITEMS),
      tracks: topTracksAccumulator(TOP_ITEMS),
    }
  })
  await history.scan(0, now + 60_000, (play) => {
    for (const p of periods) {
      if (play.playedAt < p.from || play.playedAt >= p.until) continue
      p.summary.add(play)
      p.artists.add(play)
      p.tracks.add(play)
    }
  })
  return periods.map((p) => {
    const summary = p.summary.result()
    return {
      period: p.period,
      start: p.start,
      minutes: Math.min(Math.floor(summary.listenedMs / 60_000), MAX_MINUTES[p.period]),
      streams: summary.streams,
      uniqueArtists: summary.uniqueArtists,
      uniqueTracks: summary.uniqueTracks,
      currentStreak: streaks.current,
      longestStreak: streaks.longest,
      topArtists: p.artists.result().map((r) => r.item.name.slice(0, MAX_NAME)),
      topTracks: p.tracks.result().map((r) => r.item.name.slice(0, MAX_NAME)),
    }
  })
}

/**
 * Port of PublishListeningStatsUseCase: uploads the aggregates only when the ranking is configured
 * and the user opted in. Returns whether something was uploaded.
 */
export class PublishListeningStats {
  constructor(private readonly deps: { cloud: CloudRepository; history: HistoryRepository; now: () => number }) {}

  async run(): Promise<DataResult<boolean>> {
    const { cloud, history, now } = this.deps
    if (!cloud.enabled) return success(false)
    const me = await cloud.me()
    if (!me.ok) return me
    if (!me.data.sharingEnabled) return success(false)
    const upload = await cloud.publish(await buildSharedStats(history, now()))
    return upload.ok ? success(true) : upload
  }
}

export class GetLeaderboard {
  constructor(private readonly deps: { cloud: CloudRepository; now: () => number }) {}

  ranking(metric: LeaderboardMetric, period: LeaderboardPeriod): Promise<DataResult<LeaderboardEntry[]>> {
    return this.deps.cloud.leaderboard(metric, period, periodStart(period, localDateOf(this.deps.now())))
  }

  friend(spotifyUserId: string, period: LeaderboardPeriod): Promise<DataResult<FriendStats>> {
    return this.deps.cloud.friendStats(spotifyUserId, period, periodStart(period, localDateOf(this.deps.now())))
  }
}
