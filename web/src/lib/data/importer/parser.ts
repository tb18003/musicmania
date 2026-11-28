/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { safeCoverUrl } from '$lib/domain/leaderboard'
import type { PlayEvent } from '$lib/model/music'

/**
 * Port of StreamingHistoryParser. Handles both Spotify export formats and MusicMania's own export:
 *  - Extended streaming history (`Streaming_History_Audio_*.json`, legacy `endsong_*.json`)
 *  - Basic "account data" history (`StreamingHistory*.json`, `StreamingHistory_music_*.json`)
 *  - MusicMania export (Settings → Export, to move devices): `playedAt` is epoch milliseconds from
 *    this web app or an ISO-8601 string from the Android app
 *
 * Privacy: only the fields below are ever read. `ip_addr`, `conn_country`,
 * `user_agent_decrypted` and the like are never copied, so they are never stored.
 */
interface RawEntry {
  ts?: unknown
  endTime?: unknown
  ms_played?: unknown
  msPlayed?: unknown
  master_metadata_track_name?: unknown
  trackName?: unknown
  master_metadata_album_artist_name?: unknown
  artistName?: unknown
  master_metadata_album_album_name?: unknown
  albumName?: unknown
  spotify_track_uri?: unknown
  playedAt?: unknown
  trackId?: unknown
  spotifyTrackId?: unknown
  imageUrl?: unknown
  platform?: unknown
  shuffle?: unknown
  skipped?: unknown
  reason_end?: unknown
}

const TRACK_URI_PREFIX = 'spotify:track:'
const TRACK_ID = /^[A-Za-z0-9]{1,64}$/

const text = (value: unknown): string | null => (typeof value === 'string' && value.trim() !== '' ? value : null)
const bool = (value: unknown): boolean | null => (typeof value === 'boolean' ? value : null)
const int = (value: unknown): number | null => (typeof value === 'number' && Number.isFinite(value) ? value : null)

/** Same rule as DefaultStreamingHistoryImporter.isHistoryFile (no path traversal, JSON only). */
export function isHistoryFile(path: string): boolean {
  if (path.includes('..') || !path.toLowerCase().endsWith('.json')) return false
  const name = (path.split('/').pop() ?? '').toLowerCase()
  return name.startsWith('streaming_history_audio') || name.startsWith('streaminghistory') || name.startsWith('endsong')
}

/** Extended: ISO-8601 `ts`. Basic: `endTime` "yyyy-MM-dd HH:mm" in UTC, minute precision. */
function playedAt(entry: RawEntry): number | null {
  const ts = text(entry.ts)
  if (ts) {
    const parsed = Date.parse(ts)
    return Number.isNaN(parsed) ? null : parsed
  }
  const end = text(entry.endTime)
  if (end) {
    const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})$/.exec(end)
    if (!match) return null
    const [, year, month, day, hour, minute] = match.map(Number)
    return Date.UTC(year ?? 0, (month ?? 1) - 1, day, hour, minute)
  }
  // MusicMania export: epoch milliseconds (web) or ISO-8601 (Android).
  const millis = int(entry.playedAt)
  if (millis !== null) return millis >= 0 ? millis : null
  const iso = text(entry.playedAt)
  if (!iso) return null
  const parsed = Date.parse(iso)
  return Number.isNaN(parsed) ? null : parsed
}

function trackIdOf(entry: RawEntry): string | null {
  const uri = text(entry.spotify_track_uri)
  const id = uri?.startsWith(TRACK_URI_PREFIX)
    ? uri.slice(TRACK_URI_PREFIX.length)
    : (text(entry.spotifyTrackId) ?? text(entry.trackId))
  return id && TRACK_ID.test(id) ? id : null
}

/** Maps an entry to a play, or null for podcasts, audiobooks and malformed rows. */
export function toPlayEvent(value: unknown): PlayEvent | null {
  if (typeof value !== 'object' || value === null) return null
  const entry = value as RawEntry
  const trackName = text(entry.master_metadata_track_name) ?? text(entry.trackName)
  const artistName = text(entry.master_metadata_album_artist_name) ?? text(entry.artistName)
  const at = playedAt(entry)
  const msPlayed = int(entry.ms_played) ?? int(entry.msPlayed)
  if (!trackName || !artistName || at === null || msPlayed === null) return null
  const reasonEnd = text(entry.reason_end)
  return {
    trackId: trackIdOf(entry),
    trackName,
    artistName,
    albumName: text(entry.master_metadata_album_album_name) ?? text(entry.albumName),
    imageUrl: safeCoverUrl(text(entry.imageUrl)),
    playedAt: at,
    msPlayed: Math.max(0, msPlayed),
    source: 'IMPORT',
    skipped: bool(entry.skipped) ?? (reasonEnd === null ? null : reasonEnd === 'fwdbtn'),
    shuffle: bool(entry.shuffle),
    platform: normalizePlatform(text(entry.platform)),
  }
}

/** Port of PlatformNormalizer: Spotify's verbose platform strings in a few readable buckets. */
export function normalizePlatform(raw: string | null): string | null {
  const p = raw?.toLowerCase()
  if (!p) return null
  const has = (...words: string[]) => words.some((word) => p.includes(word))
  // Web player strings also mention the host OS, so check them first.
  if (has('web')) return 'Web'
  if (has('android')) return 'Android'
  if (has('ios', 'iphone', 'ipad')) return 'iOS'
  if (has('windows')) return 'Windows'
  if (has('os x', 'macos', 'osx')) return 'macOS'
  if (has('linux')) return 'Linux'
  if (has('cast')) return 'Cast'
  if (has('sonos', 'speaker', 'echo', 'alexa')) return 'Smart speaker'
  if (has('tv', 'playstation', 'xbox')) return 'TV & consoles'
  if (has('car', 'auto')) return 'Car'
  return 'Other'
}
