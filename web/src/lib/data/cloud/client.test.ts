/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { success } from '$lib/model/result'
import type { SharedPeriodStats } from '$lib/model/leaderboard'
import { fakeFetch, json, type RecordedRequest } from '../testing'
import { MusicManiaCloudClient } from './client'

const API = 'https://musicmania-api-123.europe-southwest1.run.app/'

function client(handler: (request: RecordedRequest) => Response, apiUrl: string | null = API) {
  let invalidations = 0
  const tokens = {
    accessToken: async () => success(invalidations === 0 ? 'token-1' : 'token-2'),
    invalidate: () => void invalidations++,
  }
  const { fetcher, requests } = fakeFetch(handler)
  return { cloud: new MusicManiaCloudClient(apiUrl, tokens, fetcher), requests, invalidations: () => invalidations }
}

const WEEK: SharedPeriodStats = {
  period: 'week',
  start: '2026-11-23',
  minutes: 120,
  streams: 30,
  uniqueArtists: 5,
  uniqueTracks: 20,
  currentStreak: 3,
  longestStreak: 9,
  topArtists: [{ name: 'Coral Echo', imageUrl: 'https://i.scdn.co/image/coral' }],
  topTracks: [{ name: 'Low Tide', imageUrl: 'http://insecure.example/low-tide' }],
}

describe('MusicManiaCloudClient', () => {
  it('without an API URL it is disabled and never touches the network', async () => {
    const { cloud, requests } = client(() => json({}), null)
    expect(cloud.enabled).toBe(false)
    expect(await cloud.me()).toEqual({ ok: false, error: { kind: 'notFound' } })
    expect(requests).toHaveLength(0)
  })

  it('sends the Spotify token only to the API origin, as the bearer', async () => {
    const { cloud, requests } = client(() =>
      json({ spotifyUserId: 'ana', displayName: 'Ana', role: 'member', sharingEnabled: true }),
    )
    expect(await cloud.me()).toEqual(success({ spotifyUserId: 'ana', displayName: 'Ana', sharingEnabled: true }))
    expect(requests[0]?.url).toBe('https://musicmania-api-123.europe-southwest1.run.app/v1/me')
    expect(requests[0]?.headers.get('Authorization')).toBe('Bearer token-1')
  })

  it('uploads aggregates in the shape of api/openapi.yaml', async () => {
    const { cloud, requests } = client(() => new Response(null, { status: 204 }))
    expect(await cloud.publish([WEEK])).toEqual(success(undefined))
    expect(requests[0]?.method).toBe('PUT')
    expect(JSON.parse(requests[0]?.body ?? '')).toEqual({
      periods: [
        {
          kind: 'week',
          start: '2026-11-23',
          minutes: 120,
          streams: 30,
          uniqueArtists: 5,
          uniqueTracks: 20,
          currentStreak: 3,
          longestStreak: 9,
          topArtists: [{ name: 'Coral Echo', imageUrl: 'https://i.scdn.co/image/coral' }],
          // Only HTTPS covers are sent; the API would reject the whole upload otherwise.
          topTracks: [{ name: 'Low Tide' }],
        },
      ],
    })
  })

  it('reads the ranking, dropping malformed entries and non-https avatars', async () => {
    const { cloud, requests } = client(() =>
      json({
        entries: [
          {
            rank: 1,
            spotifyUserId: 'ana',
            displayName: 'Ana',
            avatarUrl: 'https://i.scdn.co/a',
            value: 900,
            isMe: false,
          },
          { rank: 2, spotifyUserId: 'me', avatarUrl: 'http://insecure/b', value: 400, isMe: true },
          { rank: 3 },
        ],
      }),
    )
    const result = await cloud.leaderboard('minutes', 'week', '2026-11-23')
    expect(requests[0]?.url).toContain('/v1/leaderboards?metric=minutes&period=week&start=2026-11-23')
    expect(result).toEqual(
      success([
        {
          rank: 1,
          spotifyUserId: 'ana',
          displayName: 'Ana',
          avatarUrl: 'https://i.scdn.co/a',
          value: 900,
          isMe: false,
        },
        { rank: 2, spotifyUserId: 'me', displayName: null, avatarUrl: null, value: 400, isMe: true },
      ]),
    )
  })

  it('tells "not on the allowlist" apart from other 403s', async () => {
    const notListed = client(() => json({ code: 'not_allowlisted', title: 'Forbidden', status: 403, detail: '' }, 403))
    expect(await notListed.cloud.me()).toEqual({ ok: false, error: { kind: 'notAllowlisted' } })
    const forbidden = client(() => new Response('nope', { status: 403 }))
    expect(await forbidden.cloud.me()).toEqual({ ok: false, error: { kind: 'forbidden' } })
  })

  it('a 401 refreshes the Spotify token once and retries', async () => {
    let calls = 0
    const { cloud, requests, invalidations } = client(() =>
      ++calls === 1 ? new Response(null, { status: 401 }) : new Response(null, { status: 204 }),
    )
    expect(await cloud.deleteMyData()).toEqual(success(undefined))
    expect(invalidations()).toBe(1)
    expect(requests.map((r) => [r.method, r.headers.get('Authorization')])).toEqual([
      ['DELETE', 'Bearer token-1'],
      ['DELETE', 'Bearer token-2'],
    ])
  })

  it("reads a friend's top lists with HTTPS covers only", async () => {
    const { cloud } = client(() =>
      json({
        spotifyUserId: 'ana',
        displayName: 'Ana',
        kind: 'week',
        start: '2026-11-23',
        minutes: 1_510,
        streams: 520,
        uniqueArtists: 33,
        uniqueTracks: 200,
        currentStreak: 30,
        topArtists: [{ name: 'Coral Echo', imageUrl: 'https://i.scdn.co/image/coral' }, { name: 'No Cover' }],
        topTracks: [{ name: 'Low Tide', imageUrl: 'javascript:alert(1)' }],
      }),
    )
    const result = await cloud.friendStats('ana', 'week', '2026-11-23')
    expect(result.ok && result.data.topArtists).toEqual([
      { name: 'Coral Echo', imageUrl: 'https://i.scdn.co/image/coral' },
      { name: 'No Cover', imageUrl: null },
    ])
    expect(result.ok && result.data.topTracks).toEqual([{ name: 'Low Tide', imageUrl: null }])
  })

  it('friend ids are URL-encoded', async () => {
    const { cloud, requests } = client(() =>
      json({ kind: 'week', start: '2026-11-23', minutes: 1, streams: 1, uniqueArtists: 1, uniqueTracks: 1 }),
    )
    await cloud.friendStats('a/b?c', 'week', '2026-11-23')
    expect(requests[0]?.url).toContain('/v1/users/a%2Fb%3Fc/stats?period=week&start=2026-11-23')
  })
})
