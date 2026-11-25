/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { expect, test } from '@playwright/test'
import { strToU8, zipSync } from 'fflate'
import { mockSpotify, signIn } from './spotify-mock'

test('top lists: Spotify ranges, derived albums and the all-time top from local history', async ({ page }) => {
  await mockSpotify(page)
  await signIn(page)

  await page.getByRole('button', { name: 'Top' }).click()
  await expect(page.getByText('Top short_term')).toBeVisible()
  await expect(page.getByText(/From Spotify, updated/)).toBeVisible()

  await page.getByText('6 months').click()
  await expect(page.getByText('Top medium_term')).toBeVisible()

  await page.getByText('Albums').click()
  await expect(page.getByText('Shoreline')).toBeVisible()

  await page.getByText('Artists').click()
  await expect(page.getByText('dream pop')).toBeVisible()

  // All time comes from the plays synced on sign-in: Low Tide twice, Night Drive once.
  await page.getByText('Tracks').click()
  await page.getByText('All time').click()
  await expect(page.getByText('From your history on this device')).toBeVisible()
  await expect(page.getByRole('listitem').first()).toContainText('Low Tide')
  await expect(page.getByRole('listitem').first()).toContainText('2 streams')
})

test('importing the Spotify ZIP fills history and the all-time top, in a worker, on the device', async ({ page }) => {
  const spotify = await mockSpotify(page)
  await signIn(page)

  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Import Spotify history' }).click()
  await expect(page.getByRole('heading', { name: 'Import your history' })).toBeVisible()

  const history = Array.from({ length: 30 }, (_, i) => ({
    ts: new Date(Date.UTC(2021, 5, 1 + i, 20)).toISOString(),
    ms_played: 210_000,
    ip_addr: '203.0.113.7',
    master_metadata_track_name: 'Imported Anthem',
    master_metadata_album_artist_name: 'Old Favourite',
    master_metadata_album_album_name: 'Archive',
    spotify_track_uri: 'spotify:track:old1',
  }))
  const zip = zipSync({
    'Spotify Extended Streaming History/Streaming_History_Audio_2021.json': strToU8(JSON.stringify(history)),
    'Spotify Extended Streaming History/ReadMeFirst.pdf': strToU8('not json'),
  })
  const requestsBefore = spotify.bearers.length
  await page.getByLabel('Choose ZIP or JSON files').setInputFiles({
    name: 'my_spotify_data.zip',
    mimeType: 'application/zip',
    buffer: Buffer.from(zip),
  })

  await expect(page.getByRole('heading', { name: 'Import complete' })).toBeVisible()
  await expect(page.getByText('1 files · 30 plays · 30 new')).toBeVisible()
  // The export is processed locally: importing made no network request at all.
  expect(spotify.bearers.length).toBe(requestsBefore)

  await page.getByRole('button', { name: 'Back' }).click()
  await page.getByRole('button', { name: 'Top' }).click()
  await page.getByText('All time').click()
  await expect(page.getByRole('listitem').first()).toContainText('Imported Anthem')
  await expect(page.getByRole('listitem').first()).toContainText('30 streams')
})
