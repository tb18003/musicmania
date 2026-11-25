/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import type { Cached, TopItemsRepository } from '$lib/domain/repositories'
import { GetTopItems } from '$lib/domain/top'
import type { TopArtist, TopTrack } from '$lib/model/top'
import { InMemoryHistoryRepository } from '$testing/fakes'
import { play } from '$testing/fixtures'
import { TopViewModel } from './TopViewModel.svelte'

const track = (id: string, artistName = 'A'): TopTrack => ({
  id,
  name: `Song ${id}`,
  artistName,
  albumId: `album-${id}`,
  albumName: `Album ${id}`,
  imageUrl: null,
})

class FakeTop implements TopItemsRepository {
  calls: string[] = []
  tracks: Cached<TopTrack> = {
    list: { items: [track('1'), track('2')], previousRanks: new Map([['2', 1]]), fetchedAt: 42 },
    error: null,
  }
  artists: Cached<TopArtist> = {
    list: {
      items: [{ id: 'a', name: 'Coral Echo', imageUrl: null, genres: ['indie', 'dream pop', 'shoegaze'] }],
      previousRanks: null,
      fetchedAt: 42,
    },
    error: null,
  }
  async topTracks(range: string, options?: { refresh?: boolean }) {
    this.calls.push(`tracks:${range}:${options?.refresh ? 'refresh' : 'cache'}`)
    return this.tracks
  }
  async topArtists(range: string) {
    this.calls.push(`artists:${range}`)
    return this.artists
  }
}

function setup() {
  const repository = new FakeTop()
  const history = new InMemoryHistoryRepository()
  const vm = new TopViewModel(new GetTopItems({ top: repository, history, now: () => Date.UTC(2026, 10, 25) }))
  return { vm, repository, history }
}

describe('TopViewModel', () => {
  it('starts with the 4-week top tracks, with movement against yesterday', async () => {
    const { vm } = setup()
    await vm.load()
    expect(vm.state.rows.map((r) => [r.title, r.subtitle, r.ranked.movement.kind])).toEqual([
      ['Song 1', 'A', 'new'],
      ['Song 2', 'A', 'down'],
    ])
    expect(vm.state.fetchedAt).toBe(42)
  })

  it('switching kind and range reloads; artists show their first two genres', async () => {
    const { vm, repository } = setup()
    await vm.select('artists')
    expect(vm.state.rows[0]).toMatchObject({ title: 'Coral Echo', subtitle: 'indie, dream pop' })
    await vm.selectRange('ONE_YEAR')
    expect(repository.calls.at(-1)).toBe('artists:ONE_YEAR')
    await vm.select('albums')
    expect(vm.state.rows.map((r) => r.title)).toEqual(['Album 1', 'Album 2'])
  })

  it('lifetime comes from local history and refresh forces Spotify', async () => {
    const { vm, repository, history } = setup()
    await history.addPlays([play({ trackName: 'Local', msPlayed: 200_000 })])
    await vm.selectRange('LIFETIME')
    expect(vm.state.rows.map((r) => [r.title, r.ranked.streams])).toEqual([['Local', 1]])

    await vm.selectRange('FOUR_WEEKS')
    await vm.refresh()
    expect(repository.calls.at(-1)).toBe('tracks:FOUR_WEEKS:refresh')
  })

  it('only the latest request updates the screen', async () => {
    const { vm } = setup()
    const slow = vm.select('artists')
    const fast = vm.select('tracks')
    await Promise.all([slow, fast])
    expect(vm.state.kind).toBe('tracks')
    expect(vm.state.rows[0]?.title).toBe('Song 1')
  })
})
