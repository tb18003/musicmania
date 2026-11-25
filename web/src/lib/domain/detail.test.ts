/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { success } from '$lib/model/result'
import { FakeSpotifyRemote, InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { GetItemDetail, ItemStatsAccumulator, matcher, MAX_MONTHS } from './detail'
import { feed } from './stats'

const at = (year: number, month: number, day = 10) => new Date(year, month - 1, day, 12).getTime()

describe('item statistics (observe*Totals / observe*Monthly)', () => {
  const plays = [
    play({
      trackName: 'Low Tide',
      artistName: 'Coral Echo',
      albumName: 'Shoreline',
      playedAt: at(2026, 1),
      msPlayed: 200_000,
    }),
    play({
      trackName: 'Low Tide',
      artistName: 'Coral Echo',
      albumName: 'Shoreline',
      playedAt: at(2026, 3),
      msPlayed: 100_000,
    }),
    play({
      trackName: 'Low Tide',
      artistName: 'Coral Echo',
      albumName: 'Shoreline',
      playedAt: at(2026, 3, 11),
      msPlayed: 10_000,
    }), // < 30 s
    play({
      trackName: 'High Tide',
      artistName: 'Coral Echo',
      albumName: 'Shoreline',
      playedAt: at(2026, 2),
      msPlayed: 60_000,
    }),
    play({
      trackName: 'Low Tide',
      artistName: 'Someone Else',
      albumName: 'Cover',
      playedAt: at(2026, 2),
      msPlayed: 60_000,
    }),
  ]
  const stats = (target: Parameters<typeof matcher>[0]) => {
    const accumulator = new ItemStatsAccumulator(matcher(target))
    feed(plays, accumulator)
    return accumulator.result()
  }

  it('a track: only its streams of 30 s or more, with first and last play and every month in between', () => {
    expect(stats({ kind: 'track', name: 'Low Tide', artist: 'Coral Echo', id: null, imageUrl: null })).toEqual({
      streams: 2,
      listenedMs: 300_000,
      firstPlayed: at(2026, 1),
      lastPlayed: at(2026, 3),
      monthly: [
        { month: '2026-01', streams: 1 },
        { month: '2026-02', streams: 0 },
        { month: '2026-03', streams: 1 },
      ],
    })
  })

  it('an artist counts all their tracks; an album needs its artist', () => {
    expect(stats({ kind: 'artist', name: 'Coral Echo', artist: '', id: null, imageUrl: null }).streams).toBe(3)
    expect(stats({ kind: 'album', name: 'Shoreline', artist: 'Coral Echo', id: null, imageUrl: null }).streams).toBe(3)
    expect(stats({ kind: 'album', name: 'Cover', artist: 'Coral Echo', id: null, imageUrl: null }).streams).toBe(0)
  })

  it('nothing played gives empty stats; long histories keep the last 24 months, across years', () => {
    expect(stats({ kind: 'artist', name: 'Nobody', artist: '', id: null, imageUrl: null })).toMatchObject({
      streams: 0,
      firstPlayed: null,
      monthly: [],
    })
    const accumulator = new ItemStatsAccumulator(() => true)
    feed([play({ playedAt: at(2020, 11) }), play({ playedAt: at(2026, 11) })], accumulator)
    const monthly = accumulator.result().monthly
    expect(monthly).toHaveLength(MAX_MONTHS)
    expect(monthly.at(-1)).toEqual({ month: '2026-11', streams: 1 })
    expect(monthly[0]?.month).toBe('2024-12')
  })
})

describe('GetItemDetail', () => {
  it('asks Spotify for an artist once per session, but retries after a failure', async () => {
    const remote = new FakeSpotifyRemote()
    const detail = new GetItemDetail({ history: new InMemoryHistoryRepository(), remote, now: () => Date.now() })

    expect((await detail.artist('missing')).ok).toBe(false)
    remote.artistInfo.set('missing', { genres: ['indie'], imageUrl: null })
    expect(await detail.artist('missing')).toEqual(success({ genres: ['indie'], imageUrl: null }))
    await detail.artist('missing')
    expect(remote.artistCalls).toEqual(['missing', 'missing'])
  })
})
