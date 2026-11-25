/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { expect, test } from '@playwright/test'
import { mockSpotify, signIn } from './spotify-mock'

test('Insights tab: personality, clock and heatmap from the synced plays; Settings behind the gear', async ({
  page,
}) => {
  await mockSpotify(page)
  await signIn(page)

  const tabs = page.getByRole('navigation', { name: 'Main' }).getByRole('button')
  await expect(tabs).toHaveText(['Home', 'Top', 'History', 'Insights', 'Ranking'])

  await page.getByRole('button', { name: 'Insights' }).click()
  await expect(page.getByRole('heading', { name: 'Your listening personality' })).toBeVisible()
  await expect(page.getByRole('img', { name: /Listening by hour of the day/ })).toBeVisible()
  await expect(page.getByRole('img', { name: /Listening by weekday and hour/ })).toBeVisible()
  // Synced plays carry no skip/shuffle data: the screen says where it comes from.
  await expect(page.getByText(/come from the imported Spotify history/)).toBeVisible()

  await page.getByText('All time').click()
  await expect(page.getByRole('heading', { name: 'Next goals' })).toBeVisible()

  await page.getByRole('button', { name: 'Home' }).click()
  await page.getByRole('button', { name: 'Settings' }).click()
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible()
  await page.getByRole('button', { name: 'Back' }).click()
  await expect(page.getByRole('heading', { name: /Hi, E2E Tester/ })).toBeVisible()
})
