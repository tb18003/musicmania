/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { SpotifyAuthConfig } from './spotify/auth'

interface Env {
  VITE_SPOTIFY_CLIENT_ID?: string | undefined
  VITE_SPOTIFY_REDIRECT_URI?: string | undefined
}

/**
 * Build-time configuration (public values, like SpotifyConfig from BuildConfig). Without a
 * Client ID, sign-in is simply unavailable. The redirect must be HTTPS, or loopback for local dev.
 */
export function spotifyConfigFrom(env: Env): SpotifyAuthConfig | null {
  const clientId = env.VITE_SPOTIFY_CLIENT_ID?.trim()
  const redirectUri = env.VITE_SPOTIFY_REDIRECT_URI?.trim()
  if (!clientId || !redirectUri) return null
  const url = new URL(redirectUri)
  const loopback = url.protocol === 'http:' && (url.hostname === '127.0.0.1' || url.hostname === '[::1]')
  if (url.protocol !== 'https:' && !loopback) throw new Error('VITE_SPOTIFY_REDIRECT_URI must be https')
  return { clientId, redirectUri }
}
