/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { FakeCloudRepository, InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { buildSharedStats, GetLeaderboard, periodEnd, periodStart, PublishListeningStats } from './leaderboard'

// Wednesday 25 November 2026, 18:00 local.
const NOW = new Date(2026, 10, 25, 18).getTime()
const at = (month: number, day: number) => new Date(2026, month - 1, day, 12).getTime()

describe('periods (LeaderboardPeriod)', () => {
  it('start: Monday, first of the month, first of the year, 1970 for all time', () => {
    expect(periodStart('week', '2026-11-25')).toBe('2026-11-23')
    expect(periodStart('month', '2026-11-25')).toBe('2026-11-01')
    expect(periodStart('year', '2026-11-25')).toBe('2026-01-01')
    expect(periodStart('all_time', '2026-11-25')).toBe('1970-01-01')
  })

  it('end: exclusive, across month and year boundaries', () => {
    expect(periodEnd('week', '2026-12-28')).toBe('2027-01-04')
    expect(periodEnd('month', '2026-12-01')).toBe('2027-01-01')
    expect(periodEnd('month', '2028-02-01')).toBe('2028-03-01')
    expect(periodEnd('year', '2026-01-01')).toBe('2027-01-01')
    expect(periodEnd('all_time', '1970-01-01')).toBeNull()
  })
})

describe('buildSharedStats (BuildSharedStatsUseCase)', () => {
  it('one aggregate per calendar period, with streaks and the top 10 names', async () => {
    const history = new InMemoryHistoryRepository()
    await history.addPlays([
      play({ playedAt: at(11, 25), msPlayed: 600_000, trackName: 'This week' }),
      play({ playedAt: at(11, 24), msPlayed: 600_000, trackName: 'This week too' }),
      play({ playedAt: at(11, 3), msPlayed: 600_000, trackName: 'This month', artistName: 'Nova Lights' }),
      play({ playedAt: at(2, 1), msPlayed: 600_000, trackName: 'This year' }),
      play({ playedAt: new Date(2019, 0, 1).getTime(), msPlayed: 600_000, trackName: 'Long ago' }),
    ])

    const stats = await buildSharedStats(history, NOW)

    expect(stats.map((s) => [s.period, s.start, s.minutes, s.streams])).toEqual([
      ['week', '2026-11-23', 20, 2],
      ['month', '2026-11-01', 30, 3],
      ['year', '2026-01-01', 40, 4],
      ['all_time', '1970-01-01', 50, 5],
    ])
    expect(stats[1]?.uniqueArtists).toBe(2)
    expect(stats[0]?.currentStreak).toBe(2)
    // Same streams and time: ties are broken by name, so the order is stable.
    expect(stats[0]?.topTracks).toEqual(['This week too', 'This week'])
    expect(stats[1]?.topArtists).toEqual(['Coral Echo', 'Nova Lights'])
  })

  it('never sends more than 10 names of at most 200 characters, nor more minutes than a period has', async () => {
    const history = new InMemoryHistoryRepository()
    await history.addPlays(
      Array.from({ length: 15 }, (_, i) =>
        play({ playedAt: at(11, 25) - i * 1000, trackName: `${'x'.repeat(300)}${i}`, msPlayed: 2 * 24 * 3_600_000 }),
      ),
    )
    const [week] = await buildSharedStats(history, NOW)
    expect(week?.topTracks).toHaveLength(10)
    expect(week?.topTracks.every((name) => name.length === 200)).toBe(true)
    expect(week?.minutes).toBe(7 * 24 * 60)
  })
})

describe('PublishListeningStats', () => {
  const setup = () => {
    const cloud = new FakeCloudRepository()
    const publish = new PublishListeningStats({ cloud, history: new InMemoryHistoryRepository(), now: () => NOW })
    return { cloud, publish }
  }

  it('uploads nothing without a configured API or without opting in', async () => {
    const { cloud, publish } = setup()
    expect(await publish.run()).toEqual({ ok: true, data: false })
    cloud.enabled = false
    cloud.profile = { ...cloud.profile, sharingEnabled: true }
    expect(await publish.run()).toEqual({ ok: true, data: false })
    expect(cloud.published).toHaveLength(0)
  })

  it('when sharing, every period is uploaded', async () => {
    const { cloud, publish } = setup()
    cloud.profile = { ...cloud.profile, sharingEnabled: true }
    expect(await publish.run()).toEqual({ ok: true, data: true })
    expect(cloud.published[0]?.map((s) => s.period)).toEqual(['week', 'month', 'year', 'all_time'])
  })

  it('a user who is not on the allowlist gets a typed error', async () => {
    const { cloud, publish } = setup()
    cloud.failure = { kind: 'notAllowlisted' }
    expect(await publish.run()).toEqual({ ok: false, error: { kind: 'notAllowlisted' } })
  })
})

describe('GetLeaderboard', () => {
  it('asks for the current period of the local calendar', async () => {
    const cloud = new FakeCloudRepository()
    await new GetLeaderboard({ cloud, now: () => NOW }).ranking('streak', 'month')
    expect(cloud.leaderboardRequests).toEqual([['streak', 'month', '2026-11-01']])
  })
})
