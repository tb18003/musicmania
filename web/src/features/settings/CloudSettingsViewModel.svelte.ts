/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { PublishListeningStats } from '$lib/domain/leaderboard'
import type { CloudRepository } from '$lib/domain/repositories'
import type { MessageKey } from '$lib/ui/i18n/en'

export interface CloudSettingsState {
  loaded: boolean
  sharingEnabled: boolean
  notAllowlisted: boolean
  spotifyUserId: string | null
  busy: boolean
  /** One-shot feedback (deleted / failed). */
  message: MessageKey | null
}

/** The ranking section of Settings: opt in or out, my Spotify ID, delete my server data. */
export class CloudSettingsViewModel {
  state = $state<CloudSettingsState>({
    loaded: false,
    sharingEnabled: false,
    notAllowlisted: false,
    spotifyUserId: null,
    busy: false,
    message: null,
  })
  readonly #deps: CloudSettingsDependencies

  constructor(deps: CloudSettingsDependencies) {
    this.#deps = deps
  }

  async load(): Promise<void> {
    const [me, spotifyUserId] = await Promise.all([this.#deps.cloud.me(), this.#deps.spotifyUserId()])
    this.state = {
      ...this.state,
      loaded: true,
      spotifyUserId,
      sharingEnabled: me.ok && me.data.sharingEnabled,
      notAllowlisted: !me.ok && me.error.kind === 'notAllowlisted',
      message: me.ok || me.error.kind === 'notAllowlisted' ? null : 'settings_cloud_failed',
    }
  }

  async setSharing(enabled: boolean): Promise<void> {
    this.state.busy = true
    this.state.message = null
    const result = await this.#deps.cloud.setSharing(enabled)
    if (result.ok) {
      this.state.sharingEnabled = result.data.sharingEnabled
      // Opting in uploads right away, so I appear in the ranking at once.
      if (enabled && !(await this.#deps.publish.run()).ok) this.state.message = 'settings_cloud_failed'
    } else {
      this.state.message = 'settings_cloud_failed'
    }
    this.state.busy = false
  }

  async deleteData(): Promise<void> {
    this.state.busy = true
    const result = await this.#deps.cloud.deleteMyData()
    this.state.busy = false
    if (result.ok) {
      this.state.sharingEnabled = false
      this.state.message = 'settings_cloud_deleted'
    } else {
      this.state.message = 'settings_cloud_failed'
    }
  }
}

export interface CloudSettingsDependencies {
  cloud: CloudRepository
  publish: PublishListeningStats
  spotifyUserId: () => Promise<string | null>
}
