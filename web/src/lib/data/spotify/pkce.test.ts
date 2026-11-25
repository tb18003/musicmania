/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { codeChallenge, randomString, VERIFIER_LENGTH } from './pkce'

describe('PKCE', () => {
  it('matches the RFC 7636 appendix B test vector', async () => {
    expect(await codeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
    )
  })

  it('verifiers use only unreserved characters, at a valid length, and never repeat', () => {
    const verifiers = new Set(Array.from({ length: 200 }, () => randomString(VERIFIER_LENGTH)))
    expect(verifiers.size).toBe(200)
    for (const verifier of verifiers) {
      expect(verifier).toMatch(/^[A-Za-z0-9\-._~]{64}$/)
    }
    expect(VERIFIER_LENGTH).toBeGreaterThanOrEqual(43)
    expect(VERIFIER_LENGTH).toBeLessThanOrEqual(128)
  })
})
