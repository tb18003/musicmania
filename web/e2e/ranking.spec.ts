/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { expect, test } from '@playwright/test'
import { mockSpotify, signIn } from './spotify-mock'

test('joining the ranking uploads aggregates only, then shows friends; it can be undone in Settings', async ({
  page,
}) => {
  const spotify = await mockSpotify(page)
  await signIn(page)
  // Not sharing yet: nothing has been uploaded by the sync on sign-in.
  expect(spotify.cloud.uploads).toHaveLength(0)

  await page.getByRole('button', { name: 'Ranking' }).click()
  await expect(page.getByRole('heading', { name: 'Join the ranking' })).toBeVisible()
  await page.getByRole('button', { name: 'Share my stats' }).click()

  await expect(page.getByRole('button', { name: 'Details of Ana' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Details of You' })).toBeVisible()

  // Security rule 10: only per-period aggregates leave the device, never plays.
  const upload = spotify.cloud.uploads[0] as { periods: Record<string, unknown>[] }
  expect(Object.keys(upload)).toEqual(['periods'])
  expect(upload.periods.map((p) => p.kind)).toEqual(['week', 'month', 'year', 'all_time'])
  const allowed = [
    'kind',
    'start',
    'minutes',
    'streams',
    'uniqueArtists',
    'uniqueTracks',
    'currentStreak',
    'longestStreak',
    'topArtists',
    'topTracks',
  ]
  for (const period of upload.periods) expect(Object.keys(period).every((key) => allowed.includes(key))).toBe(true)
  expect(JSON.stringify(upload)).not.toContain('playedAt')

  await page.getByRole('button', { name: 'Details of Ana' }).click()
  await expect(page.getByText('Friend Favourite')).toBeVisible()

  await page.getByRole('button', { name: 'Home' }).click()
  await page.getByRole('button', { name: 'Settings' }).click()
  await expect(page.getByRole('switch', { name: /Share my stats/ })).toBeChecked()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Delete my data from the server' }).click()
  await expect(page.getByText('Your data was deleted from the server.')).toBeVisible()
  await expect(page.getByRole('switch', { name: /Share my stats/ })).not.toBeChecked()
  expect(spotify.cloud.deleted).toBe(true)
})
