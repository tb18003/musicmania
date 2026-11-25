/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { detailHref, detailTarget, spotifyUrl } from './links'

const paramsOf = (href: string) => Object.fromEntries(new URLSearchParams(href.split('?')[1]))

describe('detail links', () => {
  it('round-trip through the URL, special characters included', () => {
    const target = {
      kind: 'track' as const,
      name: 'Rock & Roll? (Live) #1',
      artist: 'Coral Echo',
      id: '4uLU6hMCjMI75M1A2tKUQC',
      imageUrl: 'https://i.scdn.co/image/abc',
    }
    const href = detailHref(target)
    expect(href.startsWith('#/detail?')).toBe(true)
    expect(detailTarget(paramsOf(href))).toEqual(target)
  })

  it('rejects edited or malicious links', () => {
    expect(detailTarget({ kind: 'playlist', name: 'x' })).toBeNull()
    expect(detailTarget({ kind: 'track', name: 'x' })).toBeNull() // a track needs its artist
    expect(detailTarget({ kind: 'artist', name: '' })).toBeNull()
    expect(detailTarget({ kind: 'artist', name: 'x'.repeat(201) })).toBeNull()
  })

  it('drops unsafe optional fields instead of trusting them', () => {
    const target = detailTarget({
      kind: 'artist',
      name: 'Coral Echo',
      artist: 'ignored for artists',
      id: '../../v1/me',
      image: 'javascript:alert(1)',
    })
    expect(target).toEqual({ kind: 'artist', name: 'Coral Echo', artist: '', id: null, imageUrl: null })
  })

  it('opens Spotify only when the item has an id', () => {
    expect(spotifyUrl({ kind: 'album', name: 'a', artist: 'b', id: 'abc123', imageUrl: null })).toBe(
      'https://open.spotify.com/album/abc123',
    )
    expect(spotifyUrl({ kind: 'album', name: 'a', artist: 'b', id: null, imageUrl: null })).toBeNull()
  })
})
