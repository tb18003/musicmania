/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { expect, test } from '@playwright/test'
import { mockSpotify, signIn } from './spotify-mock'

test('the recap plays as stories and shares a PNG card', async ({ page }) => {
  // No share sheet in the test browser: the card is downloaded instead.
  await page.addInitScript(() => Object.defineProperty(navigator, 'canShare', { value: undefined }))
  await mockSpotify(page)
  await signIn(page)

  await page.getByRole('link', { name: 'Open recap' }).click()
  await expect(page.getByRole('dialog', { name: 'Your recap' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Main' })).toHaveCount(0)

  await page.getByRole('button', { name: 'Pause' }).click()
  for (let i = 0; i < 6; i++) await page.keyboard.press('ArrowRight')
  await expect(page.getByText('Share your recap')).toBeVisible()

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Share', exact: true }).click()
  const file = await download
  expect(file.suggestedFilename()).toBe('musicmania-recap-this_year.png')
  const stream = await file.createReadStream()
  const head = await new Promise<Buffer>((resolve) => stream.once('data', (chunk) => resolve(chunk as Buffer)))
  expect([...head.subarray(0, 4)]).toEqual([0x89, 0x50, 0x4e, 0x47]) // PNG signature

  await page.keyboard.press('Escape')
  await expect(page.getByRole('heading', { name: /Hi, E2E Tester/ })).toBeVisible()
})
