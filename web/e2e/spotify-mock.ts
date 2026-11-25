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

export interface SpotifyMock {
  /** Bearer tokens seen by api.spotify.com. */
  bearers: string[]
  /** Bodies posted to the token endpoint. */
  tokenRequests: URLSearchParams[]
  /** Hosts that received an Authorization header. */
  authorizedHosts: Set<string>
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

/** A fake Spotify (accounts + Web API) good enough for the whole sign-in and sync flow. */
export async function mockSpotify(page: Page): Promise<SpotifyMock> {
  const mock: SpotifyMock = { bearers: [], tokenRequests: [], authorizedHosts: new Set() }

  await page.route('**/*', async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    const authorization = await request.headerValue('authorization')
    if (authorization) mock.authorizedHosts.add(url.host)
    // The app under test is served locally; nothing else may leave the machine.
    if (url.hostname === '127.0.0.1') return route.continue()
    if (url.host !== 'accounts.spotify.com' && url.host !== 'api.spotify.com') return route.abort()
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: CORS })

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
    }
    return route.fulfill({ status: 404, headers: CORS })
  })
  return mock
}
