/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { SpotifyRemote } from '$lib/domain/repositories'
import type { NowPlaying, PlayEvent, RecentlyPlayedPage, UserProfile } from '$lib/model/music'
import { failure, success, type AppError, type DataResult } from '$lib/model/result'
import type { ArtistInfo } from '$lib/model/detail'
import type { ApiTimeRange, TopArtist, TopTrack } from '$lib/model/top'

const TIME_RANGE: Record<ApiTimeRange, string> = {
  FOUR_WEEKS: 'short_term',
  SIX_MONTHS: 'medium_term',
  ONE_YEAR: 'long_term',
}

/** The only host that ever receives the Spotify bearer token (security rule 5). */
export const SPOTIFY_API_ORIGIN = 'https://api.spotify.com'
const TIMEOUT_MS = 10_000

export interface TokenProvider {
  accessToken(): Promise<DataResult<string>>
  invalidate(): void
}

// ---- defensive DTO reading: Spotify's JSON is untrusted input -----------------------------------

type Json = Record<string, unknown>
const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null
const str = (value: unknown): string | null => (typeof value === 'string' && value.length > 0 ? value : null)
const num = (value: unknown): number | null => (typeof value === 'number' && Number.isFinite(value) ? value : null)
const arr = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])

/** The image closest to 300 px wide: sharp enough for lists, light on mobile data. */
export function pickImage(images: unknown): string | null {
  const candidates = arr(images)
    .filter(isObject)
    .map((image) => ({ url: str(image.url), width: num(image.width) ?? 0 }))
    .filter((image): image is { url: string; width: number } => image.url?.startsWith('https://') === true)
  if (candidates.length === 0) return null
  candidates.sort((a, b) => Math.abs(a.width - 300) - Math.abs(b.width - 300))
  return candidates[0]?.url ?? null
}

interface TrackFields {
  id: string | null
  name: string
  artistName: string
  albumName: string | null
  imageUrl: string | null
  durationMs: number
}

function readTrack(value: unknown): TrackFields | null {
  if (!isObject(value)) return null
  const name = str(value.name)
  const artist = arr(value.artists).find(isObject)
  const artistName = artist ? str(artist.name) : null
  const album = isObject(value.album) ? value.album : {}
  if (!name || !artistName) return null
  return {
    id: str(value.id),
    name,
    artistName,
    albumName: str(album.name),
    imageUrl: pickImage(album.images),
    durationMs: Math.max(0, num(value.duration_ms) ?? 0),
  }
}

/** recently-played item → play. msPlayed = track duration, as on Android (the API has no real value). */
export function toPlayEvent(item: unknown): PlayEvent | null {
  if (!isObject(item)) return null
  const track = readTrack(item.track)
  const playedAt = Date.parse(str(item.played_at) ?? '')
  if (!track || Number.isNaN(playedAt)) return null
  return {
    trackId: track.id,
    trackName: track.name,
    artistName: track.artistName,
    albumName: track.albumName,
    imageUrl: track.imageUrl,
    playedAt,
    msPlayed: track.durationMs,
    source: 'SYNC',
    skipped: null,
    shuffle: null,
    platform: null,
  }
}

export function toTopTrack(value: unknown): TopTrack | null {
  const track = readTrack(value)
  if (!track || !isObject(value)) return null
  const album = isObject(value.album) ? value.album : {}
  return {
    id: track.id,
    name: track.name,
    artistName: track.artistName,
    albumId: str(album.id),
    albumName: track.albumName,
    imageUrl: track.imageUrl,
  }
}

export function toTopArtist(value: unknown): TopArtist | null {
  if (!isObject(value)) return null
  const name = str(value.name)
  if (!name) return null
  return {
    id: str(value.id),
    name,
    imageUrl: pickImage(value.images),
    genres: arr(value.genres).filter((genre): genre is string => typeof genre === 'string'),
  }
}

// ---- client ----------------------------------------------------------------------------------------

export class SpotifyApiClient implements SpotifyRemote {
  constructor(
    private readonly tokens: TokenProvider,
    private readonly fetcher: typeof fetch,
  ) {}

  async me(): Promise<DataResult<UserProfile>> {
    const result = await this.#get('/v1/me')
    if (!result.ok) return result
    const body = result.data
    const id = isObject(body) ? str(body.id) : null
    if (!isObject(body) || !id) return failure({ kind: 'invalidData', detail: 'profile' })
    return success({ id, displayName: str(body.display_name), imageUrl: pickImage(body.images) })
  }

  async recentlyPlayed(after: number | null): Promise<DataResult<RecentlyPlayedPage>> {
    const query = new URLSearchParams({ limit: '50' })
    if (after !== null) query.set('after', String(after))
    const result = await this.#get(`/v1/me/player/recently-played?${query}`)
    if (!result.ok) return result
    const body = isObject(result.data) ? result.data : {}
    const plays = arr(body.items)
      .map(toPlayEvent)
      .filter((play): play is PlayEvent => play !== null)
    const cursors = isObject(body.cursors) ? body.cursors : {}
    const nextAfter = Number(str(cursors.after) ?? num(cursors.after) ?? Number.NaN)
    return success({ plays, nextAfter: Number.isFinite(nextAfter) ? nextAfter : null })
  }

  async nowPlaying(): Promise<DataResult<NowPlaying | null>> {
    const result = await this.#get('/v1/me/player/currently-playing')
    if (!result.ok) return result
    const body = result.data
    if (!isObject(body) || body.currently_playing_type !== 'track') return success(null)
    const track = readTrack(body.item)
    if (!track) return success(null)
    return success({
      trackName: track.name,
      artistName: track.artistName,
      imageUrl: track.imageUrl,
      isPlaying: body.is_playing === true,
      progressMs: num(body.progress_ms) ?? 0,
      durationMs: track.durationMs,
    })
  }

  topTracks(range: ApiTimeRange): Promise<DataResult<TopTrack[]>> {
    return this.#top('tracks', range, toTopTrack)
  }

  topArtists(range: ApiTimeRange): Promise<DataResult<TopArtist[]>> {
    return this.#top('artists', range, toTopArtist)
  }

  async artist(id: string): Promise<DataResult<ArtistInfo>> {
    // The id becomes part of the path: only Spotify's base62 ids are accepted.
    if (!/^[A-Za-z0-9]{1,64}$/.test(id)) return failure({ kind: 'invalidData', detail: 'artist id' })
    const result = await this.#get(`/v1/artists/${id}`)
    if (!result.ok) return result
    const artist = toTopArtist(result.data)
    if (!artist) return failure({ kind: 'invalidData', detail: 'artist' })
    return success({ genres: artist.genres, imageUrl: artist.imageUrl })
  }

  async #top<T>(type: string, range: ApiTimeRange, map: (item: unknown) => T | null): Promise<DataResult<T[]>> {
    const result = await this.#get(`/v1/me/top/${type}?time_range=${TIME_RANGE[range]}&limit=50`)
    if (!result.ok) return result
    const body = isObject(result.data) ? result.data : {}
    return success(
      arr(body.items)
        .map(map)
        .filter((item): item is T => item !== null),
    )
  }

  /** GET with the bearer token; on 401 refreshes once and retries. 204 → null body. */
  async #get(path: string, retried = false): Promise<DataResult<unknown>> {
    const url = new URL(path, SPOTIFY_API_ORIGIN)
    if (url.origin !== SPOTIFY_API_ORIGIN) throw new Error('Refusing to send the Spotify token to another host')
    const token = await this.tokens.accessToken()
    if (!token.ok) return token
    let response: Response
    try {
      response = await this.fetcher(url, {
        headers: { Authorization: `Bearer ${token.data}`, Accept: 'application/json' },
        signal: AbortSignal.timeout(TIMEOUT_MS),
      })
    } catch {
      return failure({ kind: 'network' })
    }
    if (response.status === 401 && !retried) {
      this.tokens.invalidate()
      return this.#get(path, true)
    }
    const error = errorFor(response)
    if (error) return failure(error)
    if (response.status === 204) return success(null)
    try {
      return success(await response.json())
    } catch {
      return failure({ kind: 'invalidData', detail: 'not JSON' })
    }
  }
}

function errorFor(response: Response): AppError | null {
  if (response.ok) return null
  switch (response.status) {
    case 401:
      return { kind: 'unauthorized' }
    // Development Mode: accounts outside the app's 5 allow-listed users get 403.
    case 403:
      return { kind: 'forbidden' }
    case 404:
      return { kind: 'notFound' }
    case 429: {
      const retryAfter = Number(response.headers.get('Retry-After'))
      return {
        kind: 'rateLimited',
        retryAfterSeconds: Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null,
      }
    }
    default:
      return { kind: 'server', status: response.status }
  }
}
