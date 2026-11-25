/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { failure, success } from '$lib/model/result'
import type { TopTrack } from '$lib/model/top'
import { GetTopItems } from '$lib/domain/top'
import { FakeSpotifyRemote, InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { testDatabase } from '../testing'
import { CachedTopItemsRepository, TOP_CACHE_TTL_MS } from './top-items'

const track = (id: string): TopTrack => ({
  id,
  name: id,
  artistName: 'A',
  albumId: null,
  albumName: null,
  imageUrl: null,
})
const DAY = 24 * 60 * 60_000

function setup() {
  const remote = new FakeSpotifyRemote()
  let now = new Date(2026, 10, 25, 9).getTime()
  const repository = new CachedTopItemsRepository(testDatabase(), remote, () => now)
  return { repository, remote, advance: (ms: number) => (now += ms) }
}

describe('CachedTopItemsRepository', () => {
  it('asks Spotify once, then serves today’s snapshot while fresh', async () => {
    const { repository, remote, advance } = setup()
    remote.tracks.FOUR_WEEKS = [success([track('a'), track('b')])]

    const first = await repository.topTracks('FOUR_WEEKS')
    advance(TOP_CACHE_TTL_MS - 1)
    const second = await repository.topTracks('FOUR_WEEKS')

    expect(first.list?.items.map((t) => t.id)).toEqual(['a', 'b'])
    expect(second.list?.items.map((t) => t.id)).toEqual(['a', 'b'])
    expect(remote.topCalls).toEqual(['tracks:FOUR_WEEKS'])
    expect(first.list?.previousRanks).toBeNull()
  })

  it('ranks against yesterday’s snapshot for ▲▼ NEW', async () => {
    const { repository, remote, advance } = setup()
    remote.tracks.FOUR_WEEKS = [
      success([track('a'), track('b'), track('c')]),
      success([track('c'), track('a'), track('d')]),
    ]
    await repository.topTracks('FOUR_WEEKS')
    advance(DAY)

    const today = await repository.topTracks('FOUR_WEEKS')

    expect([...(today.list?.previousRanks ?? [])]).toEqual([
      ['a', 1],
      ['b', 2],
      ['c', 3],
    ])
  })

  it('offline: the newest cached list comes back with the error', async () => {
    const { repository, remote, advance } = setup()
    remote.tracks.SIX_MONTHS = [success([track('a')]), failure({ kind: 'network' })]
    await repository.topTracks('SIX_MONTHS')
    advance(DAY)

    const offline = await repository.topTracks('SIX_MONTHS')

    expect(offline.error).toEqual({ kind: 'network' })
    expect(offline.list?.items.map((t) => t.id)).toEqual(['a'])
  })

  it('ranges and kinds never mix, and refresh bypasses the cache', async () => {
    const { repository, remote } = setup()
    remote.tracks.FOUR_WEEKS = [success([track('short')]), success([track('short-2')])]
    remote.tracks.ONE_YEAR = [success([track('long')])]
    await repository.topTracks('FOUR_WEEKS')
    expect((await repository.topTracks('ONE_YEAR')).list?.items[0]?.id).toBe('long')
    expect((await repository.topTracks('FOUR_WEEKS', { refresh: true })).list?.items[0]?.id).toBe('short-2')
    expect((await repository.topArtists('FOUR_WEEKS')).list?.items).toEqual([])
  })
})

describe('GetTopItems with the cached repository', () => {
  it('Spotify ranges come from the API with movement; lifetime from local history', async () => {
    const remote = new FakeSpotifyRemote()
    remote.tracks.FOUR_WEEKS = [
      success([
        { ...track('1'), albumId: 'x' },
        { ...track('2'), albumId: 'y' },
      ]),
    ]
    const history = new InMemoryHistoryRepository()
    await history.addPlays([play({ trackName: 'Local hit', msPlayed: 200_000 })])
    const now = () => Date.UTC(2026, 10, 25)
    const top = new GetTopItems({ top: new CachedTopItemsRepository(testDatabase(), remote, now), history, now })

    const api = await top.tracks('FOUR_WEEKS')
    expect(api.items.map((r) => [r.rank, r.item.name, r.movement.kind])).toEqual([
      [1, '1', 'unknown'],
      [2, '2', 'unknown'],
    ])
    expect(api.fetchedAt).toBe(now())

    expect((await top.albums('FOUR_WEEKS')).items.map((r) => r.item.key)).toEqual(['x', 'y'])
    expect((await top.tracks('LIFETIME')).items.map((r) => r.item.name)).toEqual(['Local hit'])
    expect(remote.topCalls).toEqual(['tracks:FOUR_WEEKS'])
  })
})
