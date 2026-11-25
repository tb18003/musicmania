/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { strToU8, zipSync } from 'fflate'
import { describe, expect, it } from 'vitest'
import type { PlayEvent } from '$lib/model/music'
import { InMemoryHistoryRepository } from '$testing/fakes'
import { DEFAULT_LIMITS, importStreamingHistory, type ImportLimits } from './importer'
import { isHistoryFile, normalizePlatform, toPlayEvent } from './parser'

/** One extended-format entry, including the private fields Spotify exports (never stored). */
const extended = (track: string | null, ts: string, ms = 180_000) => ({
  ts,
  platform: 'iOS 17.4 (iPhone15,2)',
  ms_played: ms,
  conn_country: 'ES',
  ip_addr: '203.0.113.7',
  user_agent_decrypted: 'secret-agent',
  master_metadata_track_name: track,
  master_metadata_album_artist_name: track ? 'Coral Echo' : null,
  master_metadata_album_album_name: 'Shoreline',
  spotify_track_uri: track ? 'spotify:track:abc123' : null,
  episode_name: track ? null : 'A podcast',
  reason_end: 'trackdone',
  shuffle: false,
  skipped: null,
})

const basic = (track: string, endTime: string, ms = 200_000) => ({
  endTime,
  artistName: 'Nova Lights',
  trackName: track,
  msPlayed: ms,
})

const file = (name: string, content: string | Uint8Array) => new File([content as BlobPart], name)
const json = (name: string, entries: unknown) => file(name, JSON.stringify(entries))

async function run(files: File[], limits: ImportLimits = DEFAULT_LIMITS) {
  const history = new InMemoryHistoryRepository()
  const progress: number[] = []
  const result = await importStreamingHistory(
    files,
    (plays) => history.addPlays(plays),
    (p) => progress.push(p.read),
    limits,
  )
  return { result, history, progress }
}

describe('importStreamingHistory', () => {
  it('streams the Spotify ZIP: only history files, both formats, podcasts skipped', async () => {
    const zip = zipSync({
      'Spotify Extended Streaming History/Streaming_History_Audio_2023.json': strToU8(
        JSON.stringify([
          extended('Low Tide', '2023-05-01T10:00:00Z'),
          extended('High Tide', '2023-05-01T11:00:00Z'),
          extended(null, '2023-05-01T12:00:00Z'), // podcast
        ]),
      ),
      'Spotify Account Data/StreamingHistory_music_0.json': strToU8(
        JSON.stringify([basic('Night Drive', '2024-01-02 21:30')]),
      ),
      'Spotify Extended Streaming History/ReadMeFirst_ExtendedStreamingHistory.pdf': strToU8('%PDF not json'),
      'Spotify Account Data/Userdata.json': strToU8('{"email":"never-read@example.com"}'),
    })

    const { result, history, progress } = await run([file('my_spotify_data.zip', zip)])

    expect(result).toEqual({
      ok: true,
      data: {
        files: 2,
        read: 3,
        inserted: 3,
        skipped: 1,
        earliest: Date.parse('2023-05-01T10:00:00Z'),
        latest: Date.UTC(2024, 0, 2, 21, 30),
      },
    })
    expect(progress.length).toBeGreaterThan(0)
    expect(history.plays.map((p) => p.trackName)).toEqual(['Low Tide', 'High Tide', 'Night Drive'])
  })

  it('never stores IP, country or user agent from the export', async () => {
    const { history } = await run([
      json('Streaming_History_Audio_2023.json', [extended('Low Tide', '2023-05-01T10:00:00Z')]),
    ])
    const stored = JSON.stringify(history.plays)
    expect(stored).not.toContain('203.0.113.7')
    expect(stored).not.toContain('secret-agent')
    expect(stored).not.toContain('conn_country')
    expect(history.plays[0]).toMatchObject({ trackId: 'abc123', platform: 'iOS', source: 'IMPORT', skipped: false })
  })

  it('importing the same export twice adds nothing the second time', async () => {
    const history = new InMemoryHistoryRepository()
    const files = [json('endsong_0.json', [extended('Low Tide', '2023-05-01T10:00:00Z')])]
    const sink = (plays: readonly PlayEvent[]) => history.addPlays(plays)
    await importStreamingHistory(files, sink, () => undefined)
    const again = await importStreamingHistory(files, sink, () => undefined)
    expect(again).toMatchObject({ ok: true, data: { read: 1, inserted: 0 } })
  })

  it('stops a zip bomb: an entry that inflates past the limit is aborted', async () => {
    const bomb = zipSync({ 'Streaming_History_Audio_bomb.json': new Uint8Array(5_000_000) }, { level: 9 })
    expect(bomb.length).toBeLessThan(50_000)
    const { result, history } = await run([file('bomb.zip', bomb)], { ...DEFAULT_LIMITS, maxEntryBytes: 1_000_000 })
    expect(result).toMatchObject({ ok: false, error: { kind: 'invalidData' } })
    expect(history.plays).toHaveLength(0)
  })

  it('caps the total uncompressed size and the number of entries', async () => {
    const entries = Object.fromEntries(
      Array.from({ length: 5 }, (_, i) => [
        `StreamingHistory${i}.json`,
        strToU8(JSON.stringify([basic(`T${i}`, '2024-01-02 21:30')])),
      ]),
    )
    const tooMany = await run([file('many.zip', zipSync(entries))], { ...DEFAULT_LIMITS, maxZipEntries: 3 })
    expect(tooMany.result).toMatchObject({ ok: false, error: { detail: 'Too many files in archive' } })

    const tooBig = await run([file('big.zip', zipSync(entries))], { ...DEFAULT_LIMITS, maxTotalBytes: 100 })
    expect(tooBig.result).toMatchObject({ ok: false, error: { detail: 'The export is too large' } })
  })

  it('rejects files that are not streaming history, and exports without any', async () => {
    expect((await run([file('Streaming_History_Audio_2023.json', '{not json')])).result).toMatchObject({
      ok: false,
      error: { kind: 'invalidData' },
    })
    expect((await run([json('StreamingHistory0.json', { not: 'an array' })])).result).toMatchObject({ ok: false })
    expect((await run([file('photos.zip', zipSync({ 'cat.json': strToU8('[]') }))])).result).toMatchObject({
      ok: false,
      error: { detail: 'No streaming history files found' },
    })
  })
})

describe('parser', () => {
  it('accepts only history files, never paths that climb out of the archive', () => {
    expect(isHistoryFile('Spotify Extended Streaming History/Streaming_History_Audio_2019-2020_0.json')).toBe(true)
    expect(isHistoryFile('MyData/StreamingHistory_music_0.json')).toBe(true)
    expect(isHistoryFile('endsong_3.json')).toBe(true)
    expect(isHistoryFile('../Streaming_History_Audio_evil.json')).toBe(false)
    expect(isHistoryFile('Streaming_History_Video_2023.json')).toBe(false)
    expect(isHistoryFile('StreamingHistory.txt')).toBe(false)
  })

  it('reads the basic format as UTC minutes and a forward-button end as a skip', () => {
    expect(toPlayEvent(basic('Night Drive', '2024-01-02 21:30'))?.playedAt).toBe(Date.UTC(2024, 0, 2, 21, 30))
    expect(
      toPlayEvent({ ...extended('X', '2023-05-01T10:00:00Z'), skipped: null, reason_end: 'fwdbtn' })?.skipped,
    ).toBe(true)
    expect(toPlayEvent({ ...basic('X', 'yesterday') })).toBeNull()
    expect(toPlayEvent('nonsense')).toBeNull()
  })

  it('normalises platforms like the app', () => {
    expect(normalizePlatform('WebPlayer (websocket RFC6455) Windows 10')).toBe('Web')
    expect(normalizePlatform('Android OS 14 API 34 (Google, Pixel 8)')).toBe('Android')
    expect(normalizePlatform('OS X 14.1 [x86 8]')).toBe('macOS')
    expect(normalizePlatform('Partner sonos_amp')).toBe('Smart speaker')
    expect(normalizePlatform(null)).toBeNull()
  })
})
