/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { DetailKind, DetailTarget } from '$lib/model/detail'

const MAX_NAME = 200
const MAX_URL = 2048
const SPOTIFY_ID = /^[A-Za-z0-9]{1,64}$/
const KINDS: readonly DetailKind[] = ['track', 'artist', 'album']

/** Link to a detail screen, as a hash URL usable in an <a href>. */
export function detailHref(target: Omit<DetailTarget, 'id' | 'imageUrl'> & Partial<DetailTarget>): string {
  const params = new URLSearchParams({ kind: target.kind, name: target.name })
  if (target.artist) params.set('artist', target.artist)
  if (target.id) params.set('id', target.id)
  if (target.imageUrl) params.set('image', target.imageUrl)
  return `#/detail?${params}`
}

/**
 * Reads a detail target from route parameters. The URL can be edited or shared, so every field is
 * validated; anything unexpected gives null (the app then goes home).
 */
export function detailTarget(params: Readonly<Record<string, string>>): DetailTarget | null {
  const kind = params.kind as DetailKind
  const name = params.name?.trim() ?? ''
  const artist = params.artist?.trim() ?? ''
  if (!KINDS.includes(kind) || name.length === 0 || name.length > MAX_NAME || artist.length > MAX_NAME) return null
  if (kind !== 'artist' && artist.length === 0) return null
  const id = params.id && SPOTIFY_ID.test(params.id) ? params.id : null
  const image = params.image
  const imageUrl = image?.startsWith('https://') && image.length <= MAX_URL ? image : null
  return { kind, name, artist: kind === 'artist' ? '' : artist, id, imageUrl }
}

/** "Open in Spotify" (the app if installed, the web player otherwise). */
export const spotifyUrl = (target: DetailTarget): string | null =>
  target.id ? `https://open.spotify.com/${target.kind}/${target.id}` : null
