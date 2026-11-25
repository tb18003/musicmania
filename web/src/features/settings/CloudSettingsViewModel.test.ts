/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { PublishListeningStats } from '$lib/domain/leaderboard'
import { FakeCloudRepository, InMemoryHistoryRepository } from '$testing/fakes'
import { CloudSettingsViewModel } from './CloudSettingsViewModel.svelte'

function setup() {
  const cloud = new FakeCloudRepository()
  const publish = new PublishListeningStats({ cloud, history: new InMemoryHistoryRepository(), now: () => 0 })
  const vm = new CloudSettingsViewModel({ cloud, publish, spotifyUserId: async () => 'ana_01' })
  return { cloud, vm }
}

describe('CloudSettingsViewModel', () => {
  it('opting in to the ranking publishes right away', async () => {
    const { cloud, vm } = setup()
    await vm.load()
    expect(vm.state).toMatchObject({ loaded: true, sharingEnabled: false, spotifyUserId: 'ana_01' })

    await vm.setSharing(true)

    expect(cloud.profile.sharingEnabled).toBe(true)
    expect(cloud.published).toHaveLength(1)
    expect(vm.state.sharingEnabled).toBe(true)
  })

  it('a failed upload after opting in is reported; sharing stays on for the next sync', async () => {
    const { cloud, vm } = setup()
    cloud.publishFailure = { kind: 'network' }
    await vm.setSharing(true)
    expect(vm.state.message).toBe('settings_cloud_failed')
    expect(cloud.profile.sharingEnabled).toBe(true)
  })

  it('accounts outside the allowlist see why', async () => {
    const { cloud, vm } = setup()
    cloud.failure = { kind: 'notAllowlisted' }
    await vm.load()
    expect(vm.state).toMatchObject({ notAllowlisted: true, message: null })
  })

  it('deleting server data stops sharing and confirms it', async () => {
    const { cloud, vm } = setup()
    cloud.profile = { ...cloud.profile, sharingEnabled: true }
    await vm.load()
    await vm.deleteData()
    expect(cloud.deleted).toBe(true)
    expect(vm.state).toMatchObject({ sharingEnabled: false, message: 'settings_cloud_deleted' })
  })
})
