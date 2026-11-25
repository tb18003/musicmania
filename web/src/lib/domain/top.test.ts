/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import type { TopTrack } from '$lib/model/top'
import { play } from '$testing/fixtures'
import { albumsFromTracks, localTopAlbums, localTopArtists, localTopTracks, rankMovements } from './top'

const track = (id: string, albumId: string | null = null): TopTrack => ({
  id,
  name: id,
  artistName: 'A',
  albumId,
  albumName: albumId,
  imageUrl: null,
})

describe('rankMovements (same cases as StatsCalculatorsTest.kt)', () => {
  it('without previous snapshot every movement is unknown', () => {
    expect(rankMovements(['a', 'b'], null)).toEqual([{ kind: 'unknown' }, { kind: 'unknown' }])
  })

  it('computes up, down, same and new', () => {
    const previous = new Map([
      ['a', 3],
      ['b', 1],
      ['c', 2],
    ])
    expect(rankMovements(['a', 'c', 'b', 'd'], previous)).toEqual([
      { kind: 'up', places: 2 },
      { kind: 'same' },
      { kind: 'down', places: 2 },
      { kind: 'new' },
    ])
  })
})

describe('albumsFromTracks (TopDerivations)', () => {
  it('albums are ranked by Borda score of their tracks', () => {
    const albums = albumsFromTracks([track('1', 'x'), track('2', 'y'), track('3', 'y')])
    // x = 3 points, y = 2 + 1 = 3 points: the tie keeps first-seen order.
    expect(albums.map((a) => [a.key, a.score])).toEqual([
      ['x', 3],
      ['y', 3],
    ])
  })
})

describe('local tops (PlayEventDao queries)', () => {
  const plays = [
    play({ trackName: 'A', artistName: 'X', albumName: 'Alpha', msPlayed: 200_000 }),
    play({ trackName: 'A', artistName: 'X', albumName: 'Alpha', msPlayed: 200_000, playedAt: 2 }),
    play({ trackName: 'B', artistName: 'Y', albumName: 'Beta', msPlayed: 300_000, playedAt: 3 }),
    play({ trackName: 'C', artistName: 'Y', albumName: null, msPlayed: 300_000, playedAt: 4 }),
    play({ trackName: 'D', artistName: 'Z', albumName: 'Delta', msPlayed: 10_000, playedAt: 5 }), // not a stream
  ]

  it('tracks: streams first, then listening time; short plays ignored', () => {
    expect(localTopTracks(plays).map((r) => [r.rank, r.item.name, r.streams])).toEqual([
      [1, 'A', 2],
      [2, 'B', 1],
      [3, 'C', 1],
    ])
  })

  it('artists and albums (albums need a name)', () => {
    expect(localTopArtists(plays).map((r) => [r.item.name, r.streams, r.listenedMs])).toEqual([
      ['Y', 2, 600_000],
      ['X', 2, 400_000],
    ])
    expect(localTopAlbums(plays).map((r) => r.item.name)).toEqual(['Alpha', 'Beta'])
  })
})
