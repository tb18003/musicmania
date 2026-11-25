/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { GetLeaderboard, PublishListeningStats } from '$lib/domain/leaderboard'
import type { CloudRepository } from '$lib/domain/repositories'
import type { FriendStats, LeaderboardEntry, LeaderboardMetric, LeaderboardPeriod } from '$lib/model/leaderboard'
import type { AppError } from '$lib/model/result'

export type LeaderboardContent =
  | { kind: 'loading' }
  /** Not on the server's allowlist: show the Spotify ID to send to the admin. */
  | { kind: 'notAllowlisted'; spotifyUserId: string | null }
  /** On the allowlist, but not sharing yet (opt-in). */
  | { kind: 'sharingOff' }
  | { kind: 'ranking'; entries: LeaderboardEntry[] }
  | { kind: 'error'; error: AppError }

export interface LeaderboardState {
  metric: LeaderboardMetric
  period: LeaderboardPeriod
  content: LeaderboardContent
  expanded: { spotifyUserId: string; stats: FriendStats | null } | null
  enablingSharing: boolean
}

export interface LeaderboardDependencies {
  cloud: CloudRepository
  leaderboard: GetLeaderboard
  publish: PublishListeningStats
  /** My Spotify ID (from Spotify itself), shown when I'm not allow-listed yet. */
  spotifyUserId: () => Promise<string | null>
}

/** Port of LeaderboardViewModel, including the upload-on-opt-in and the one-time self-heal. */
export class LeaderboardViewModel {
  state = $state<LeaderboardState>({
    metric: 'minutes',
    period: 'week',
    content: { kind: 'loading' },
    expanded: null,
    enablingSharing: false,
  })
  readonly #deps: LeaderboardDependencies
  #request = 0
  /** One automatic upload per screen if I share but am missing from the ranking. */
  #triedSelfHeal = false

  constructor(deps: LeaderboardDependencies) {
    this.#deps = deps
  }

  selectMetric(metric: LeaderboardMetric) {
    this.state.metric = metric
    this.state.expanded = null
    return this.load()
  }

  selectPeriod(period: LeaderboardPeriod) {
    this.state.period = period
    this.state.expanded = null
    return this.load()
  }

  /** Opt in, upload my stats right away so I appear immediately, then show the ranking. */
  async enableSharing(): Promise<void> {
    this.state.enablingSharing = true
    const result = await this.#deps.cloud.setSharing(true)
    const failure = result.ok ? await this.#publishError() : result.error
    this.state.enablingSharing = false
    // A failed upload is shown (with retry) instead of an empty, misleading ranking.
    if (failure) this.state.content = await this.#contentFor(failure)
    else await this.load()
  }

  async toggleFriend(spotifyUserId: string): Promise<void> {
    if (this.state.expanded?.spotifyUserId === spotifyUserId) {
      this.state.expanded = null
      return
    }
    this.state.expanded = { spotifyUserId, stats: null }
    const result = await this.#deps.leaderboard.friend(spotifyUserId, this.state.period)
    if (this.state.expanded?.spotifyUserId === spotifyUserId) {
      this.state.expanded = { spotifyUserId, stats: result.ok ? result.data : null }
    }
  }

  async load(): Promise<void> {
    const request = ++this.#request
    this.state.content = { kind: 'loading' }
    const me = await this.#deps.cloud.me()
    let content: LeaderboardContent
    if (!me.ok) content = await this.#contentFor(me.error)
    else if (!me.data.sharingEnabled) content = { kind: 'sharingOff' }
    else content = await this.#ranking()
    if (request === this.#request) this.state.content = content
  }

  async #ranking(): Promise<LeaderboardContent> {
    const result = await this.#deps.leaderboard.ranking(this.state.metric, this.state.period)
    if (!result.ok) return this.#contentFor(result.error)
    // I share but I'm not there (e.g. the upload never happened): upload once and fetch again.
    if (!result.data.some((entry) => entry.isMe) && !this.#triedSelfHeal) {
      this.#triedSelfHeal = true
      if ((await this.#publishError()) === null) return this.#ranking()
    }
    return { kind: 'ranking', entries: result.data }
  }

  async #publishError(): Promise<AppError | null> {
    const result = await this.#deps.publish.run()
    return result.ok ? null : result.error
  }

  async #contentFor(error: AppError): Promise<LeaderboardContent> {
    return error.kind === 'notAllowlisted'
      ? { kind: 'notAllowlisted', spotifyUserId: await this.#deps.spotifyUserId() }
      : { kind: 'error', error }
  }
}
