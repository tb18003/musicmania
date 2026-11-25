/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** package.json "version", injected at build time. */
declare const __APP_VERSION__: string

interface ImportMetaEnv {
  readonly VITE_SPOTIFY_CLIENT_ID?: string
  readonly VITE_SPOTIFY_REDIRECT_URI?: string
  readonly VITE_MUSICMANIA_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** Safari: true when launched from the home screen. */
interface Navigator {
  readonly standalone?: boolean
}
