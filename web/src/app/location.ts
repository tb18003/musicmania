/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** Every destination, like the @Serializable routes in MusicManiaNavHost. */
export const ROUTES = [
  '/',
  '/top',
  '/ranking',
  '/history',
  '/insights',
  '/detail',
  '/import',
  '/install',
  '/settings',
] as const
export type Route = (typeof ROUTES)[number]

export interface Location {
  readonly path: Route
  /** Query parameters after the path ("#/detail?kind=track&name=…"); validated by each screen. */
  readonly params: Readonly<Record<string, string>>
}

/** "#/settings" → "/settings". Anything unknown (or tampered with) goes home, without params. */
export function parseHash(hash: string): Location {
  const [rawPath = '', query = ''] = hash.replace(/^#/, '').split('?', 2)
  const path = rawPath || '/'
  if (!(ROUTES as readonly string[]).includes(path)) return { path: '/', params: {} }
  return { path: path as Route, params: Object.fromEntries(new URLSearchParams(query)) }
}
