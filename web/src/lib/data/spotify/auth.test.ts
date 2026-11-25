/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { fakeFetch, json, testDatabase, type RecordedRequest } from '../testing'
import { PENDING_LOGIN_TTL_MS, SpotifyAuth } from './auth'
import { codeChallenge } from './pkce'
import { TokenStore } from './token-store'

const CONFIG = { clientId: 'client-123', redirectUri: 'https://example.github.io/musicmania/auth/callback' }

function setup(handler: (request: RecordedRequest) => Response | Promise<Response>) {
  const db = testDatabase()
  const tokens = new TokenStore(db)
  const { fetcher, requests } = fakeFetch(handler)
  let now = 1_800_000_000_000
  const auth = new SpotifyAuth(CONFIG, { db, tokens, fetch: fetcher, now: () => now })
  return { auth, tokens, requests, db, advance: (ms: number) => (now += ms) }
}

const tokenResponse = (access = 'access-1', refresh: string | undefined = 'refresh-1') =>
  json({ access_token: access, token_type: 'Bearer', expires_in: 3600, refresh_token: refresh })

async function login(auth: SpotifyAuth) {
  const authorize = new URL(await auth.beginLogin())
  const state = authorize.searchParams.get('state') ?? ''
  return { authorize, callback: `${CONFIG.redirectUri}?code=auth-code&state=${state}` }
}

describe('SpotifyAuth', () => {
  it('builds a PKCE S256 authorization request for read-only scopes', async () => {
    const { auth, db } = setup(() => tokenResponse())
    const { authorize } = await login(auth)

    expect(authorize.origin + authorize.pathname).toBe('https://accounts.spotify.com/authorize')
    expect(authorize.searchParams.get('client_id')).toBe('client-123')
    expect(authorize.searchParams.get('redirect_uri')).toBe(CONFIG.redirectUri)
    expect(authorize.searchParams.get('code_challenge_method')).toBe('S256')
    expect(authorize.searchParams.get('scope')).toBe(
      'user-read-recently-played user-top-read user-read-currently-playing',
    )
    const pending = (await db.secrets.get('auth.pending'))?.value as { verifier: string }
    expect(authorize.searchParams.get('code_challenge')).toBe(await codeChallenge(pending.verifier))
  })

  it('exchanges the code with the verifier and stores only the refresh token', async () => {
    const { auth, tokens, requests, db } = setup(() => tokenResponse())
    const { callback } = await login(auth)
    const verifier = ((await db.secrets.get('auth.pending'))?.value as { verifier: string }).verifier

    expect(await auth.completeLogin(callback)).toEqual({ ok: true, data: undefined })

    const form = new URLSearchParams(requests[0]?.body ?? '')
    expect(requests[0]?.url).toBe('https://accounts.spotify.com/api/token')
    expect(Object.fromEntries(form)).toEqual({
      grant_type: 'authorization_code',
      code: 'auth-code',
      redirect_uri: CONFIG.redirectUri,
      client_id: 'client-123',
      code_verifier: verifier,
    })
    expect(form.has('client_secret')).toBe(false)
    expect(await tokens.readRefreshToken()).toBe('refresh-1')
    expect(await db.secrets.get('auth.pending')).toBeUndefined()
    expect(await auth.accessToken()).toEqual({ ok: true, data: 'access-1' })
  })

  it('rejects a callback whose state does not match (CSRF), without calling Spotify', async () => {
    const { auth, requests } = setup(() => tokenResponse())
    await login(auth)
    const result = await auth.completeLogin(`${CONFIG.redirectUri}?code=x&state=forged`)
    expect(result).toMatchObject({ ok: false, error: { kind: 'invalidData', detail: 'state mismatch' } })
    expect(requests).toHaveLength(0)
  })

  it('the login state is single use and expires', async () => {
    const { auth, requests, advance } = setup(() => tokenResponse())
    const { callback } = await login(auth)
    advance(PENDING_LOGIN_TTL_MS + 1)
    expect(await auth.completeLogin(callback)).toMatchObject({ ok: false, error: { detail: 'login expired' } })
    expect(await auth.completeLogin(callback)).toMatchObject({ ok: false, error: { detail: 'no login in progress' } })
    expect(requests).toHaveLength(0)
  })

  it('a denied consent is reported as unauthorized', async () => {
    const { auth } = setup(() => tokenResponse())
    await login(auth)
    expect(await auth.completeLogin(`${CONFIG.redirectUri}?error=access_denied&state=x`)).toMatchObject({
      ok: false,
      error: { kind: 'unauthorized' },
    })
  })

  it('concurrent callers share a single refresh, and a rotated refresh token is kept', async () => {
    let refreshes = 0
    const { auth, tokens, advance } = setup(async (request) => {
      const form = new URLSearchParams(request.body ?? '')
      if (form.get('grant_type') === 'authorization_code') return tokenResponse('access-1', 'refresh-1')
      refreshes++
      await new Promise((resolve) => setTimeout(resolve, 10))
      return tokenResponse('access-2', 'refresh-2')
    })
    await auth.completeLogin((await login(auth)).callback)
    advance(3600 * 1000) // expired

    const results = await Promise.all([auth.accessToken(), auth.accessToken(), auth.accessToken()])

    expect(refreshes).toBe(1)
    expect(results.every((result) => result.ok && result.data === 'access-2')).toBe(true)
    expect(await tokens.readRefreshToken()).toBe('refresh-2')
  })

  it('a revoked refresh token ends the session and wipes it', async () => {
    const { auth, tokens, advance } = setup((request) =>
      new URLSearchParams(request.body ?? '').get('grant_type') === 'refresh_token'
        ? json({ error: 'invalid_grant', error_description: 'Refresh token revoked' }, 400)
        : tokenResponse(),
    )
    await auth.completeLogin((await login(auth)).callback)
    advance(3600 * 1000)

    const result = await auth.accessToken()

    expect(result).toEqual({ ok: false, error: { kind: 'sessionExpired' } })
    expect(await tokens.readRefreshToken()).toBeNull()
    expect(await auth.isSignedIn()).toBe(false)
  })

  it('errors never carry the token endpoint response (which can echo secrets)', async () => {
    const { auth } = setup(() => json({ error: 'invalid_grant', code: 'auth-code' }, 400))
    const result = await auth.completeLogin((await login(auth)).callback)
    expect(JSON.stringify(result)).not.toContain('auth-code')
  })
})
