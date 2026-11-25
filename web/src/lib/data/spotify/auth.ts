/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { AuthRepository } from '$lib/domain/repositories'
import { failure, success, type DataResult } from '$lib/model/result'
import type { MusicManiaDatabase } from '../db/database'
import { codeChallenge, randomString, STATE_LENGTH, VERIFIER_LENGTH } from './pkce'
import type { TokenStore } from './token-store'

export interface SpotifyAuthConfig {
  clientId: string
  redirectUri: string
}

/** What the web client reads: no library or playlist access (the app needs those, the web not yet). */
export const SPOTIFY_SCOPES = ['user-read-recently-played', 'user-top-read', 'user-read-currently-playing'] as const

const AUTHORIZE_URL = 'https://accounts.spotify.com/authorize'
const TOKEN_URL = 'https://accounts.spotify.com/api/token'
const PENDING = 'auth.pending'
/** A login must come back within this time; the PKCE verifier is single use. */
export const PENDING_LOGIN_TTL_MS = 10 * 60_000
/** Refresh a little before expiry so a request never leaves with a dying token. */
const EXPIRY_MARGIN_MS = 60_000

interface PendingLogin {
  verifier: string
  state: string
  createdAt: number
}

interface TokenResponse {
  access_token: string
  expires_in: number
  refresh_token?: string
}

const isTokenResponse = (value: unknown): value is TokenResponse =>
  typeof value === 'object' &&
  value !== null &&
  typeof (value as TokenResponse).access_token === 'string' &&
  typeof (value as TokenResponse).expires_in === 'number'

/**
 * Authorization Code + PKCE against Spotify (public client: there is no secret). Pending login
 * state lives in IndexedDB rather than sessionStorage so it survives iOS handing the redirect to
 * a different browsing context of the same app. Refresh is single-flight, like
 * TokenRefreshCoordinator: concurrent callers share one request.
 */
export class SpotifyAuth implements AuthRepository {
  #access: { token: string; expiresAt: number } | null = null
  #refreshing: Promise<DataResult<string>> | null = null

  constructor(
    private readonly config: SpotifyAuthConfig | null,
    private readonly deps: {
      db: MusicManiaDatabase
      tokens: TokenStore
      fetch: typeof fetch
      now: () => number
    },
  ) {}

  get configured(): boolean {
    return this.config !== null
  }

  async beginLogin(): Promise<string> {
    if (!this.config) throw new Error('Spotify is not configured')
    const pending: PendingLogin = {
      verifier: randomString(VERIFIER_LENGTH),
      state: randomString(STATE_LENGTH),
      createdAt: this.deps.now(),
    }
    await this.deps.db.secrets.put({ key: PENDING, value: pending })
    const url = new URL(AUTHORIZE_URL)
    url.search = new URLSearchParams({
      client_id: this.config.clientId,
      response_type: 'code',
      redirect_uri: this.config.redirectUri,
      code_challenge_method: 'S256',
      code_challenge: await codeChallenge(pending.verifier),
      state: pending.state,
      scope: SPOTIFY_SCOPES.join(' '),
    }).toString()
    return url.toString()
  }

  async completeLogin(callbackUrl: string): Promise<DataResult<void>> {
    if (!this.config) return failure({ kind: 'invalidData', detail: 'not configured' })
    const params = new URL(callbackUrl).searchParams
    const pending = (await this.deps.db.secrets.get(PENDING))?.value as PendingLogin | undefined
    // Single use, whatever happens next.
    await this.deps.db.secrets.delete(PENDING)

    if (params.get('error')) return failure({ kind: 'unauthorized' })
    const code = params.get('code')
    if (!pending || !code) return failure({ kind: 'invalidData', detail: 'no login in progress' })
    if (this.deps.now() - pending.createdAt > PENDING_LOGIN_TTL_MS) {
      return failure({ kind: 'invalidData', detail: 'login expired' })
    }
    if (params.get('state') !== pending.state) return failure({ kind: 'invalidData', detail: 'state mismatch' })

    const result = await this.#tokenRequest({
      grant_type: 'authorization_code',
      code,
      redirect_uri: this.config.redirectUri,
      client_id: this.config.clientId,
      code_verifier: pending.verifier,
    })
    if (!result.ok) return result
    if (!result.data.refresh_token) return failure({ kind: 'invalidData', detail: 'no refresh token' })
    await this.deps.tokens.saveRefreshToken(result.data.refresh_token)
    return success(undefined)
  }

  async isSignedIn(): Promise<boolean> {
    return (await this.deps.tokens.readRefreshToken()) !== null
  }

  /** A valid access token, refreshing it (once, for every concurrent caller) when needed. */
  async accessToken(): Promise<DataResult<string>> {
    if (this.#access && this.#access.expiresAt - EXPIRY_MARGIN_MS > this.deps.now()) {
      return success(this.#access.token)
    }
    this.#refreshing ??= this.#refresh().finally(() => {
      this.#refreshing = null
    })
    return this.#refreshing
  }

  /** Called after a 401: the next accessToken() call refreshes. */
  invalidate(): void {
    this.#access = null
  }

  /** Forgets the in-memory token (sign-out wipes the stored ones). */
  forget(): void {
    this.#access = null
  }

  async #refresh(): Promise<DataResult<string>> {
    if (!this.config) return failure({ kind: 'sessionExpired' })
    const refreshToken = await this.deps.tokens.readRefreshToken()
    if (!refreshToken) return failure({ kind: 'sessionExpired' })
    const result = await this.#tokenRequest({
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: this.config.clientId,
    })
    if (!result.ok) {
      // Revoked or expired refresh token: the session is over, the user must sign in again.
      if (result.error.kind === 'unauthorized') {
        await this.deps.tokens.clear()
        return failure({ kind: 'sessionExpired' })
      }
      return result
    }
    // Spotify may rotate the refresh token: keep the newest one.
    if (result.data.refresh_token) await this.deps.tokens.saveRefreshToken(result.data.refresh_token)
    return success(result.data.access_token)
  }

  async #tokenRequest(form: Record<string, string>): Promise<DataResult<TokenResponse>> {
    let response: Response
    try {
      response = await this.deps.fetch(TOKEN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(form),
        signal: AbortSignal.timeout(15_000),
      })
    } catch {
      return failure({ kind: 'network' })
    }
    // 400 invalid_grant / 401 invalid_client: never echo the body, it may reflect the code.
    if (response.status === 400 || response.status === 401) return failure({ kind: 'unauthorized' })
    if (response.status === 429) return failure({ kind: 'rateLimited', retryAfterSeconds: null })
    if (!response.ok) return failure({ kind: 'server', status: response.status })
    let body: unknown
    try {
      body = await response.json()
    } catch {
      return failure({ kind: 'invalidData', detail: 'token response is not JSON' })
    }
    if (!isTokenResponse(body)) return failure({ kind: 'invalidData', detail: 'unexpected token response' })
    this.#access = { token: body.access_token, expiresAt: this.deps.now() + body.expires_in * 1000 }
    return success(body)
  }
}
