/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

export type InstallPlatform = 'ios' | 'android' | 'other'

interface NavigatorLike {
  userAgent: string
  maxTouchPoints: number
  standalone?: boolean
}

/** iPhone, iPod and iPad (iPadOS presents itself as a Mac, but with touch). */
export function installPlatform(nav: NavigatorLike): InstallPlatform {
  if (/iPhone|iPad|iPod/.test(nav.userAgent)) return 'ios'
  if (/Macintosh/.test(nav.userAgent) && nav.maxTouchPoints > 1) return 'ios'
  if (/Android/.test(nav.userAgent)) return 'android'
  return 'other'
}

/** Running from the home screen (installed), not in a browser tab. */
export function isStandalone(
  win: { matchMedia: (query: string) => { matches: boolean } },
  nav: NavigatorLike,
): boolean {
  return nav.standalone === true || win.matchMedia('(display-mode: standalone)').matches
}
