/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { contentSecurityPolicy } from './csp.config'

const directive = (policy: string, name: string) =>
  policy
    .split('; ')
    .find((part) => part.startsWith(`${name} `))
    ?.split(' ')
    .slice(1)

describe('content security policy', () => {
  const policy = contentSecurityPolicy('https://musicmania-api-123.europe-southwest1.run.app/')

  it('only runs first-party scripts: no inline code, no eval', () => {
    expect(directive(policy, 'script-src')).toEqual(["'self'"])
    expect(policy).not.toContain('unsafe-eval')
    expect(directive(policy, 'object-src')).toEqual(["'none'"])
    expect(directive(policy, 'base-uri')).toEqual(["'none'"])
  })

  it('talks only to Spotify and the configured API origin', () => {
    expect(directive(policy, 'connect-src')).toEqual([
      "'self'",
      'https://accounts.spotify.com',
      'https://api.spotify.com',
      'https://musicmania-api-123.europe-southwest1.run.app',
    ])
  })

  it('without an API, the ranking host is simply not allowed', () => {
    expect(directive(contentSecurityPolicy(undefined), 'connect-src')).toEqual([
      "'self'",
      'https://accounts.spotify.com',
      'https://api.spotify.com',
    ])
  })
})
