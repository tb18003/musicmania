/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { Page, Route } from '@playwright/test'

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type, Accept',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
}

export interface CloudMock {
  sharing: boolean
  /** Bodies of PUT /v1/me/stats. */
  uploads: unknown[]
  deleted: boolean
}

export interface SpotifyMock {
  /** Bearer tokens seen by api.spotify.com. */
  bearers: string[]
  /** Bodies posted to the token endpoint. */
  tokenRequests: URLSearchParams[]
  /** Hosts that received an Authorization header. */
  authorizedHosts: Set<string>
  /** The fake MusicMania API. */
  cloud: CloudMock
}

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()

const item = (name: string, artist: string, minutes: number) => ({
  played_at: minutesAgo(minutes),
  track: {
    id: name.replace(/\W/g, ''),
    name,
    duration_ms: 200_000,
    artists: [{ name: artist }],
    album: { name: 'E2E' },
  },
})

/** Signs in through the mocked Spotify and waits for the first sync. */
export async function signIn(page: Page) {
  await page.goto('./')
  await page.getByRole('button', { name: 'Sign in with Spotify' }).click()
  await page.getByText('Synced', { exact: false }).waitFor()
}

/** A fake Spotify (accounts + Web API) good enough for the whole sign-in and sync flow. */
export async function mockSpotify(page: Page): Promise<SpotifyMock> {
  const mock: SpotifyMock = {
    bearers: [],
    tokenRequests: [],
    authorizedHosts: new Set(),
    cloud: { sharing: false, uploads: [], deleted: false },
  }

  await page.route('**/*', async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    const authorization = await request.headerValue('authorization')
    if (authorization) mock.authorizedHosts.add(url.host)
    // The app under test is served locally; nothing else may leave the machine.
    if (url.hostname === '127.0.0.1' || url.protocol === 'blob:' || url.protocol === 'data:') return route.continue()
    const hosts = ['accounts.spotify.com', 'api.spotify.com', 'api.musicmania.test']
    if (!hosts.includes(url.host)) return route.abort()
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: CORS })
    if (url.host === 'api.musicmania.test')
      return fakeCloud(route, url, request.method(), request.postData(), mock.cloud)

    if (url.host === 'accounts.spotify.com' && url.pathname === '/authorize') {
      const back = new URL(url.searchParams.get('redirect_uri') ?? '')
      back.searchParams.set('code', 'e2e-code')
      back.searchParams.set('state', url.searchParams.get('state') ?? '')
      // The consent page sends the user back (WebKit cannot fulfill a mocked 302).
      return route.fulfill({
        contentType: 'text/html',
        body: `<meta http-equiv="refresh" content="0;url=${back.toString().replace(/&/g, '&amp;')}">`,
      })
    }
    if (url.host === 'accounts.spotify.com' && url.pathname === '/api/token') {
      mock.tokenRequests.push(new URLSearchParams(request.postData() ?? ''))
      return route.fulfill({
        headers: CORS,
        json: { access_token: 'e2e-access', token_type: 'Bearer', expires_in: 3600, refresh_token: 'e2e-refresh' },
      })
    }
    if (url.host === 'api.spotify.com') {
      mock.bearers.push(authorization ?? '')
      if (url.pathname === '/v1/me') {
        return route.fulfill({ headers: CORS, json: { id: 'e2e', display_name: 'E2E Tester', images: [] } })
      }
      if (url.pathname === '/v1/me/player/recently-played') {
        return route.fulfill({
          headers: CORS,
          json: {
            items: [
              item('Low Tide', 'Coral Echo', 5),
              item('Night Drive', 'Nova Lights', 10),
              item('Low Tide', 'Coral Echo', 15),
            ],
            cursors: { after: String(Date.now()) },
          },
        })
      }
      if (url.pathname === '/v1/me/player/currently-playing') return route.fulfill({ status: 204, headers: CORS })
      if (url.pathname === '/v1/me/top/tracks') {
        const range = url.searchParams.get('time_range')
        return route.fulfill({
          headers: CORS,
          json: {
            items: [
              { ...item(`Top ${range}`, 'Coral Echo', 0).track, album: { id: 'shore', name: 'Shoreline' } },
              { ...item('Second Song', 'Nova Lights', 0).track, album: { id: 'night', name: 'Nightfall' } },
            ],
          },
        })
      }
      if (url.pathname === '/v1/me/top/artists') {
        return route.fulfill({
          headers: CORS,
          json: { items: [{ id: 'ce', name: 'Coral Echo', genres: ['dream pop'], images: [] }] },
        })
      }
    }
    return route.fulfill({ status: 404, headers: CORS })
  })
  return mock
}

const FRIEND = {
  spotifyUserId: 'ana',
  displayName: 'Ana',
  kind: 'week',
  start: '2026-11-23',
  minutes: 1_510,
  streams: 520,
  uniqueArtists: 33,
  uniqueTracks: 200,
  currentStreak: 30,
  longestStreak: 41,
  topArtists: [{ name: 'Friend Favourite' }],
  topTracks: [{ name: 'Friend Anthem' }],
}

/** The MusicMania API for one signed-in user, with one friend who shares. */
function fakeCloud(route: Route, url: URL, method: string, body: string | null, cloud: CloudMock) {
  const me = () => ({ spotifyUserId: 'e2e', displayName: 'E2E Tester', role: 'member', sharingEnabled: cloud.sharing })
  const ok = (json: unknown) => route.fulfill({ headers: CORS, json })
  const empty = () => route.fulfill({ status: 204, headers: CORS })
  switch (`${method} ${url.pathname}`) {
    case 'GET /v1/me':
      return ok(me())
    case 'PUT /v1/me/settings':
      cloud.sharing = (JSON.parse(body ?? '{}') as { sharingEnabled: boolean }).sharingEnabled
      return ok(me())
    case 'PUT /v1/me/stats':
      cloud.uploads.push(JSON.parse(body ?? 'null'))
      return empty()
    case 'DELETE /v1/me':
      cloud.sharing = false
      cloud.deleted = true
      return empty()
    case 'GET /v1/leaderboards': {
      const entries = [{ rank: 1, spotifyUserId: 'ana', displayName: 'Ana', value: 1_510, isMe: false }]
      if (cloud.sharing && cloud.uploads.length > 0)
        entries.push({ rank: 2, spotifyUserId: 'e2e', displayName: 'E2E Tester', value: 10, isMe: true })
      return ok({ metric: url.searchParams.get('metric'), period: url.searchParams.get('period'), entries })
    }
    case 'GET /v1/users/ana/stats':
      return ok(FRIEND)
    default:
      return route.fulfill({ status: 404, headers: CORS })
  }
}
