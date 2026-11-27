/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { expect, test } from '@playwright/test'
import { mockSpotify, signIn } from './spotify-mock'

test('history and top rows open the detail screen; broken links go home', async ({ page }) => {
  await mockSpotify(page)
  await signIn(page)

  await page.getByRole('button', { name: 'History', exact: true }).click()
  await page.getByRole('link', { name: /Night Drive/ }).click()
  await expect(page.getByRole('heading', { name: 'Night Drive' })).toBeVisible()
  await expect(page.getByText('Nova Lights')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Open in Spotify' })).toHaveAttribute(
    'href',
    'https://open.spotify.com/track/NightDrive',
  )
  await page.getByRole('button', { name: 'Back' }).click()
  await expect(page.getByRole('heading', { name: 'History' })).toBeVisible()

  await page.getByRole('button', { name: 'Top', exact: true }).click()
  await page.getByText('Artists').click()
  await page.getByRole('link', { name: /Coral Echo/ }).click()
  await expect(page.getByRole('heading', { name: 'Coral Echo' })).toBeVisible()
  // Synced on sign-in: two plays of Low Tide by Coral Echo.
  await expect(page.getByText('Streams', { exact: true })).toBeVisible()

  await page.goto('./#/detail?kind=playlist&name=nope')
  await expect(page.getByRole('heading', { name: /Hi, E2E Tester/ })).toBeVisible()
})
