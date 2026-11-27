/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { CloudRepository } from '$lib/domain/repositories'
import { safeCoverUrl } from '$lib/domain/leaderboard'
import type {
  CloudProfile,
  FriendStats,
  LeaderboardEntry,
  LeaderboardMetric,
  LeaderboardPeriod,
  RankedItem,
  SharedPeriodStats,
} from '$lib/model/leaderboard'
import { failure, success, type AppError, type DataResult } from '$lib/model/result'
import type { TokenProvider } from '../spotify/client'
import type { components } from './schema'

type Schemas = components['schemas']
type Me = components['responses']['Me']['content']['application/json']

const TIMEOUT_MS = 15_000

/**
 * Client of the MusicMania API, typed from api/openapi.yaml. The Spotify token is the credential
 * (the API verifies it with Spotify), and it is only ever sent to the configured API origin.
 */
export class MusicManiaCloudClient implements CloudRepository {
  readonly #origin: string | null

  constructor(
    apiUrl: string | null,
    private readonly tokens: TokenProvider,
    private readonly fetcher: typeof fetch,
  ) {
    this.#origin = apiUrl ? new URL(apiUrl).origin : null
  }

  get enabled(): boolean {
    return this.#origin !== null
  }

  async me(): Promise<DataResult<CloudProfile>> {
    const result = await this.#call<Me>('GET', '/v1/me')
    return result.ok ? success(toProfile(result.data)) : result
  }

  async setSharing(enabled: boolean): Promise<DataResult<CloudProfile>> {
    const result = await this.#call<Me>('PUT', '/v1/me/settings', { sharingEnabled: enabled })
    return result.ok ? success(toProfile(result.data)) : result
  }

  async publish(stats: readonly SharedPeriodStats[]): Promise<DataResult<void>> {
    const body: Schemas['StatsUpload'] = { periods: stats.map(toDto) }
    const result = await this.#call<null>('PUT', '/v1/me/stats', body)
    return result.ok ? success(undefined) : result
  }

  async leaderboard(
    metric: LeaderboardMetric,
    period: LeaderboardPeriod,
    start: string,
  ): Promise<DataResult<LeaderboardEntry[]>> {
    const query = new URLSearchParams({ metric, period, start })
    const result = await this.#call<Schemas['Leaderboard']>('GET', `/v1/leaderboards?${query}`)
    if (!result.ok) return result
    const entries = (result.data?.entries ?? []).flatMap((entry) =>
      entry.spotifyUserId && entry.rank !== undefined
        ? [
            {
              rank: entry.rank,
              spotifyUserId: entry.spotifyUserId,
              displayName: entry.displayName ?? null,
              avatarUrl: entry.avatarUrl?.startsWith('https://') ? entry.avatarUrl : null,
              value: entry.value ?? 0,
              isMe: entry.isMe === true,
            },
          ]
        : [],
    )
    return success(entries)
  }

  async friendStats(spotifyUserId: string, period: LeaderboardPeriod, start: string): Promise<DataResult<FriendStats>> {
    const query = new URLSearchParams({ period, start })
    const result = await this.#call<Schemas['UserStats']>(
      'GET',
      `/v1/users/${encodeURIComponent(spotifyUserId)}/stats?${query}`,
    )
    if (!result.ok) return result
    const stats = result.data
    if (!stats) return failure({ kind: 'notFound' })
    return success({
      spotifyUserId: stats.spotifyUserId ?? spotifyUserId,
      displayName: stats.displayName ?? null,
      minutes: stats.minutes,
      streams: stats.streams,
      uniqueArtists: stats.uniqueArtists,
      currentStreak: stats.currentStreak ?? 0,
      topArtists: (stats.topArtists ?? []).map(fromItemDto),
      topTracks: (stats.topTracks ?? []).map(fromItemDto),
    })
  }

  async deleteMyData(): Promise<DataResult<void>> {
    const result = await this.#call<null>('DELETE', '/v1/me')
    return result.ok ? success(undefined) : result
  }

  async #call<T>(method: string, path: string, body?: unknown, retried = false): Promise<DataResult<T | null>> {
    if (!this.#origin) return failure({ kind: 'notFound' })
    const url = new URL(path, this.#origin)
    if (url.origin !== this.#origin) throw new Error('Refusing to send the token to another host')
    const token = await this.tokens.accessToken()
    if (!token.ok) return token
    let response: Response
    try {
      response = await this.fetcher(url, {
        method,
        headers: {
          Authorization: `Bearer ${token.data}`,
          Accept: 'application/json',
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      })
    } catch {
      return failure({ kind: 'network' })
    }
    // The API answers 401 when Spotify no longer accepts the token: refresh once and retry.
    if (response.status === 401 && !retried) {
      this.tokens.invalidate()
      return this.#call(method, path, body, true)
    }
    if (!response.ok) return failure(await errorFor(response))
    if (response.status === 204) return success(null)
    try {
      return success((await response.json()) as T)
    } catch {
      return failure({ kind: 'invalidData', detail: 'not JSON' })
    }
  }
}

async function errorFor(response: Response): Promise<AppError> {
  if (response.status === 403) {
    let code: unknown
    try {
      code = ((await response.json()) as Partial<Schemas['Problem']>).code
    } catch {
      // A 403 without a problem body is a plain forbidden.
    }
    return code === 'not_allowlisted' ? { kind: 'notAllowlisted' } : { kind: 'forbidden' }
  }
  if (response.status === 401) return { kind: 'unauthorized' }
  if (response.status === 404) return { kind: 'notFound' }
  if (response.status === 429 || response.status === 503) {
    const retryAfter = Number(response.headers.get('Retry-After'))
    return { kind: 'rateLimited', retryAfterSeconds: Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null }
  }
  return { kind: 'server', status: response.status }
}

const toProfile = (me: Me | null): CloudProfile => ({
  spotifyUserId: me?.spotifyUserId ?? '',
  displayName: me?.displayName ?? null,
  sharingEnabled: me?.sharingEnabled === true,
})

const toDto = (stats: SharedPeriodStats): Schemas['PeriodStats'] => ({
  kind: stats.period,
  start: stats.start,
  minutes: stats.minutes,
  streams: stats.streams,
  uniqueArtists: stats.uniqueArtists,
  uniqueTracks: stats.uniqueTracks,
  currentStreak: stats.currentStreak,
  longestStreak: stats.longestStreak,
  topArtists: stats.topArtists.map(toItemDto),
  topTracks: stats.topTracks.map(toItemDto),
})

/** Only an HTTPS cover is sent; the API rejects anything else. */
const toItemDto = (item: RankedItem): Schemas['TopItem'] => {
  const imageUrl = safeCoverUrl(item.imageUrl)
  return imageUrl ? { name: item.name, imageUrl } : { name: item.name }
}

/** A friend's cover is shown only if it is HTTPS (the CSP limits images to the Spotify CDN anyway). */
const fromItemDto = (item: Schemas['TopItem']): RankedItem => ({
  name: item.name,
  imageUrl: safeCoverUrl(item.imageUrl),
})
