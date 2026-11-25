/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { success } from '$lib/model/result'
import { fakeFetch, json, type RecordedRequest } from '../testing'
import { pickImage, SpotifyApiClient, toPlayEvent, type TokenProvider } from './client'

function tokens(): TokenProvider & { invalidations: number; issued: string[] } {
  const issued = ['token-1', 'token-2', 'token-3']
  const provider = {
    invalidations: 0,
    issued: [] as string[],
    accessToken: async () => {
      const token = issued[provider.invalidations] ?? 'token-x'
      provider.issued.push(token)
      return success(token)
    },
    invalidate: () => void provider.invalidations++,
  }
  return provider
}

const ITEM = {
  played_at: '2026-11-24T21:30:00.123Z',
  track: {
    id: 'track1',
    name: 'Low Tide',
    duration_ms: 201_000,
    artists: [
      { id: 'a1', name: 'Coral Echo' },
      { id: 'a2', name: 'Guest' },
    ],
    album: {
      name: 'Shoreline',
      images: [
        { url: 'https://i.scdn.co/image/640', width: 640, height: 640 },
        { url: 'https://i.scdn.co/image/300', width: 300, height: 300 },
        { url: 'https://i.scdn.co/image/64', width: 64, height: 64 },
      ],
    },
  },
}

function client(handler: (request: RecordedRequest) => Response) {
  const provider = tokens()
  const { fetcher, requests } = fakeFetch(handler)
  return { api: new SpotifyApiClient(provider, fetcher), provider, requests }
}

describe('SpotifyApiClient', () => {
  it('maps recently-played like the Android sync: first artist, duration as listening time', async () => {
    const { api, requests } = client(() =>
      json({ items: [ITEM, { broken: true }], cursors: { after: '1764019800123' } }),
    )

    const page = await api.recentlyPlayed(1_700_000_000_000)

    expect(requests[0]?.url).toBe('https://api.spotify.com/v1/me/player/recently-played?limit=50&after=1700000000000')
    expect(page).toEqual(
      success({
        plays: [
          {
            trackId: 'track1',
            trackName: 'Low Tide',
            artistName: 'Coral Echo',
            albumName: 'Shoreline',
            imageUrl: 'https://i.scdn.co/image/300',
            playedAt: Date.parse('2026-11-24T21:30:00.123Z'),
            msPlayed: 201_000,
            source: 'SYNC',
            skipped: null,
            shuffle: null,
            platform: null,
          },
        ],
        nextAfter: 1_764_019_800_123,
      }),
    )
  })

  it('sends the bearer token, and on 401 refreshes once and retries', async () => {
    let calls = 0
    const { api, provider, requests } = client(() =>
      ++calls === 1 ? new Response(null, { status: 401 }) : json({ id: 'me' }),
    )

    expect(await api.me()).toEqual(success({ id: 'me', displayName: null, imageUrl: null }))
    expect(provider.invalidations).toBe(1)
    expect(requests.map((r) => r.headers.get('Authorization'))).toEqual(['Bearer token-1', 'Bearer token-2'])
  })

  it('a second 401 is returned, not retried forever', async () => {
    const { api, requests } = client(() => new Response(null, { status: 401 }))
    expect(await api.me()).toEqual({ ok: false, error: { kind: 'unauthorized' } })
    expect(requests).toHaveLength(2)
  })

  it('maps 403 (not one of the 5 Development Mode users) and 429 with Retry-After', async () => {
    expect(await client(() => new Response(null, { status: 403 })).api.me()).toEqual({
      ok: false,
      error: { kind: 'forbidden' },
    })
    const limited = client(() => new Response(null, { status: 429, headers: { 'Retry-After': '17' } }))
    expect(await limited.api.me()).toEqual({ ok: false, error: { kind: 'rateLimited', retryAfterSeconds: 17 } })
  })

  it('nothing playing (204) and podcasts are "nothing playing"', async () => {
    expect(await client(() => new Response(null, { status: 204 })).api.nowPlaying()).toEqual(success(null))
    const episode = client(() => json({ currently_playing_type: 'episode', item: {} }))
    expect(await episode.api.nowPlaying()).toEqual(success(null))
  })

  it('the token only ever goes to api.spotify.com', async () => {
    const { api, requests } = client(() => json({ items: [] }))
    await api.me()
    await api.recentlyPlayed(null)
    await api.nowPlaying()
    expect(new Set(requests.map((r) => new URL(r.url).origin))).toEqual(new Set(['https://api.spotify.com']))
  })

  it('reads one artist (genres and picture), refusing ids that are not Spotify ids', async () => {
    const { api, requests } = client(() =>
      json({
        id: 'a1',
        name: 'Coral Echo',
        genres: ['dream pop', 7],
        images: [{ url: 'https://i.scdn.co/a', width: 320 }],
      }),
    )
    expect(await api.artist('a1')).toEqual(success({ genres: ['dream pop'], imageUrl: 'https://i.scdn.co/a' }))
    expect(requests[0]?.url).toBe('https://api.spotify.com/v1/artists/a1')
    expect(await api.artist('../me')).toMatchObject({ ok: false, error: { kind: 'invalidData' } })
    expect(requests).toHaveLength(1)
  })

  it('network failures are typed', async () => {
    const api = new SpotifyApiClient(tokens(), (async () => {
      throw new TypeError('Failed to fetch')
    }) as typeof fetch)
    expect(await api.me()).toEqual({ ok: false, error: { kind: 'network' } })
  })
})

describe('DTO guards', () => {
  it('drops malformed plays instead of failing the whole page', () => {
    expect(toPlayEvent({ played_at: 'yesterday', track: ITEM.track })).toBeNull()
    expect(toPlayEvent({ played_at: ITEM.played_at, track: { name: 'No artist' } })).toBeNull()
    expect(toPlayEvent(null)).toBeNull()
  })

  it('only accepts https images', () => {
    expect(pickImage([{ url: 'http://insecure/img', width: 300 }])).toBeNull()
    expect(pickImage('nope')).toBeNull()
  })
})
