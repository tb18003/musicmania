/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { installPlatform, isStandalone } from './platform'

const IPHONE =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 Version/18.6 Mobile Safari/604.1'
const IPAD = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.6 Safari/605.1.15'
const ANDROID = 'Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 Chrome/142.0 Mobile Safari/537.36'

describe('platform', () => {
  it('recognises iPhone, iPadOS (a "Mac" with touch) and Android', () => {
    expect(installPlatform({ userAgent: IPHONE, maxTouchPoints: 5 })).toBe('ios')
    expect(installPlatform({ userAgent: IPAD, maxTouchPoints: 5 })).toBe('ios')
    expect(installPlatform({ userAgent: IPAD, maxTouchPoints: 0 })).toBe('other')
    expect(installPlatform({ userAgent: ANDROID, maxTouchPoints: 5 })).toBe('android')
  })

  it('detects the installed app in Safari and in other browsers', () => {
    const media = (matches: boolean) => ({ matchMedia: () => ({ matches }) })
    expect(isStandalone(media(false), { userAgent: IPHONE, maxTouchPoints: 5, standalone: true })).toBe(true)
    expect(isStandalone(media(true), { userAgent: ANDROID, maxTouchPoints: 5 })).toBe(true)
    expect(isStandalone(media(false), { userAgent: IPHONE, maxTouchPoints: 5, standalone: false })).toBe(false)
  })
})
