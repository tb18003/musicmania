/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Renders the PNG icons from public/icons/icon.svg with the Playwright browser we already use for
// tests (no image tooling dependency). Run after changing the SVG: npm run icons
import { readFileSync } from 'node:fs'
import { chromium } from '@playwright/test'

const svg = readFileSync(new URL('../public/icons/icon.svg', import.meta.url), 'utf8')
const out = (name) => new URL(`../public/icons/${name}`, import.meta.url)

// The artwork already sits inside Android's adaptive-icon safe zone (66 of 108 units), which is
// stricter than the 80 % maskable zone, so the same full-bleed square serves every purpose.
const targets = [
  ['apple-touch-icon.png', 180],
  ['icon-192.png', 192],
  ['icon-512.png', 512],
  ['icon-maskable-512.png', 512],
]

const browser = await chromium.launch()
try {
  const page = await browser.newPage()
  for (const [name, size] of targets) {
    await page.setViewportSize({ width: size, height: size })
    await page.setContent(
      `<html><body style="margin:0">${svg.replace('width="108" height="108"', `width="${size}" height="${size}"`)}</body></html>`,
    )
    await page.locator('svg').screenshot({ path: out(name).pathname.replace(/^\/([A-Za-z]:)/, '$1') })
  }
} finally {
  await browser.close()
}
