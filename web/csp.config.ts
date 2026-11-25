/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Content Security Policy for the production build. GitHub Pages cannot send custom headers,
 * so it is delivered as a <meta http-equiv> tag (frame-ancestors and report-uri are not
 * available that way). Scripts are first-party only: no inline code, no eval, no third parties.
 */
export function contentSecurityPolicy(apiUrl: string | undefined): string {
  const apiOrigin = apiUrl ? new URL(apiUrl).origin : undefined
  const connect = ["'self'", 'https://accounts.spotify.com', 'https://api.spotify.com', apiOrigin].filter(Boolean)
  const directives: Record<string, string[]> = {
    'default-src': ["'self'"],
    'script-src': ["'self'"],
    // Svelte sets some styles inline (style attributes, transitions); scripts stay strict.
    'style-src': ["'self'", "'unsafe-inline'"],
    // Album art and profile pictures from Spotify's CDNs (Facebook-linked accounts use fbcdn).
    'img-src': [
      "'self'",
      'data:',
      'https://*.scdn.co',
      'https://*.spotifycdn.com',
      'https://*.fbcdn.net',
      'https://platform-lookaside.fbsbx.com',
    ],
    'font-src': ["'self'"],
    'connect-src': connect as string[],
    'worker-src': ["'self'"],
    'manifest-src': ["'self'"],
    'object-src': ["'none'"],
    'base-uri': ["'none'"],
    'form-action': ["'none'"],
  }
  return Object.entries(directives)
    .map(([name, values]) => `${name} ${values.join(' ')}`)
    .join('; ')
}
