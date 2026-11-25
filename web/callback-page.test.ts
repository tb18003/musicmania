/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8').replace(/\r\n/g, '\n')

describe('Android App Link fallback page', () => {
  // The web build owns /musicmania/* on GitHub Pages, including the Android redirect path.
  it('the web build ships exactly the documented page', () => {
    expect(read('./public/callback.html')).toBe(read('../docs/app-links/callback/index.html'))
  })

  it('never reads the authorization code: no script at all', () => {
    expect(read('./public/callback.html')).not.toMatch(/<script/i)
  })
})
