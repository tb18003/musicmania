/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { GetLeaderboard, PublishListeningStats } from '$lib/domain/leaderboard'
import type { LeaderboardEntry } from '$lib/model/leaderboard'
import { FakeCloudRepository, InMemoryHistoryRepository } from '$testing/fakes'
import { LeaderboardViewModel } from './LeaderboardViewModel.svelte'

const NOW = () => new Date(2026, 10, 25, 18).getTime()

function setup() {
  const cloud = new FakeCloudRepository()
  const vm = new LeaderboardViewModel({
    cloud,
    leaderboard: new GetLeaderboard({ cloud, now: NOW }),
    publish: new PublishListeningStats({ cloud, history: new InMemoryHistoryRepository(), now: NOW }),
    spotifyUserId: async () => 'my-spotify-id',
  })
  return { cloud, vm }
}

const entries: LeaderboardEntry[] = [
  { rank: 1, spotifyUserId: 'ana', displayName: 'Ana', avatarUrl: null, value: 1_510, isMe: false },
  { rank: 2, spotifyUserId: 'me', displayName: 'Me', avatarUrl: null, value: 1_240, isMe: true },
]

describe('LeaderboardViewModel (same cases as LeaderboardViewModelTest.kt)', () => {
  it('accounts outside the allowlist see their Spotify id to send to the admin', async () => {
    const { cloud, vm } = setup()
    cloud.failure = { kind: 'notAllowlisted' }
    await vm.load()
    expect(vm.state.content).toEqual({ kind: 'notAllowlisted', spotifyUserId: 'my-spotify-id' })
  })

  it('nothing is shown until the user opts in', async () => {
    const { vm } = setup()
    await vm.load()
    expect(vm.state.content).toEqual({ kind: 'sharingOff' })
  })

  it('opting in publishes right away and shows the ranking', async () => {
    const { cloud, vm } = setup()
    cloud.entries = entries
    await vm.load()

    await vm.enableSharing()

    expect(cloud.published).toHaveLength(1)
    expect(vm.state.content).toEqual({ kind: 'ranking', entries })
    expect(vm.state.enablingSharing).toBe(false)
  })

  it('a failed upload when opting in offers a retry instead of an empty ranking', async () => {
    const { cloud, vm } = setup()
    cloud.publishFailure = { kind: 'network' }
    await vm.enableSharing()
    expect(vm.state.content).toEqual({ kind: 'error', error: { kind: 'network' } })
  })

  it('sharing but missing from the ranking uploads once and fetches again', async () => {
    const { cloud, vm } = setup()
    cloud.profile = { ...cloud.profile, sharingEnabled: true }
    cloud.entries = entries.filter((e) => !e.isMe)

    await vm.load()
    expect(cloud.published).toHaveLength(1)
    expect(cloud.leaderboardRequests).toHaveLength(2)

    await vm.selectPeriod('year')
    expect(cloud.published).toHaveLength(1)
  })

  it('changing metric and period asks for the right ranking', async () => {
    const { cloud, vm } = setup()
    cloud.profile = { ...cloud.profile, sharingEnabled: true }
    cloud.entries = entries
    await vm.selectMetric('streak')
    await vm.selectPeriod('year')
    expect(cloud.leaderboardRequests.at(-1)).toEqual(['streak', 'year', '2026-01-01'])
  })

  it('tapping a friend opens their details with covers, closing clears them', async () => {
    const { cloud, vm } = setup()
    cloud.friends.set('ana', {
      spotifyUserId: 'ana',
      displayName: 'Ana',
      minutes: 1_510,
      streams: 520,
      uniqueArtists: 33,
      currentStreak: 30,
      topArtists: [{ name: 'Coral Echo', imageUrl: 'https://i.scdn.co/image/coral' }],
      topTracks: [{ name: 'Low Tide', imageUrl: null }],
    })
    const opening = vm.openFriend('ana')
    expect(vm.state.selected).toEqual({ spotifyUserId: 'ana', stats: null, loading: true })
    await opening
    expect(vm.state.selected?.loading).toBe(false)
    expect(vm.state.selected?.stats?.topArtists).toEqual([
      { name: 'Coral Echo', imageUrl: 'https://i.scdn.co/image/coral' },
    ])
    vm.closeFriend()
    expect(vm.state.selected).toBeNull()
  })

  it('a friend whose stats cannot be loaded ends loading without stats', async () => {
    const { vm } = setup()
    await vm.openFriend('nobody')
    expect(vm.state.selected).toEqual({ spotifyUserId: 'nobody', stats: null, loading: false })
  })

  it('closing before the stats arrive keeps the dialog closed', async () => {
    const { cloud, vm } = setup()
    cloud.friends.set('ana', {
      spotifyUserId: 'ana',
      displayName: 'Ana',
      minutes: 1,
      streams: 1,
      uniqueArtists: 1,
      currentStreak: 0,
      topArtists: [],
      topTracks: [],
    })
    const opening = vm.openFriend('ana')
    vm.closeFriend()
    await opening
    expect(vm.state.selected).toBeNull()
  })

  it('server errors offer a retry', async () => {
    const { cloud, vm } = setup()
    cloud.failure = { kind: 'network' }
    await vm.load()
    expect(vm.state.content.kind).toBe('error')
  })
})
