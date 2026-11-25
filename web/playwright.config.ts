/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { defineConfig, devices } from '@playwright/test'

const PORT = 4173
export const E2E_CLIENT_ID = 'e2e-client-id'
export const E2E_API_URL = 'https://api.musicmania.test'

/**
 * End-to-end tests run against the production build (CSP and service worker included),
 * on WebKit: Safari's engine, which is what iPhone users get.
 */
export default defineConfig({
  testDir: 'e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://127.0.0.1:${PORT}/musicmania/`,
    trace: 'retain-on-failure',
    // Requests a service worker makes bypass page.route(), and would reach the real Spotify.
    serviceWorkers: 'block',
  },
  projects: [{ name: 'iphone-webkit', use: { ...devices['iPhone 15'] } }],
  // Its own build, with a fake Client ID: Spotify itself is simulated in the tests (page.route).
  webServer: {
    command: `npx vite build --mode e2e --outDir dist-e2e && npx vite preview --outDir dist-e2e --host 127.0.0.1 --port ${PORT} --strictPort`,
    url: `http://127.0.0.1:${PORT}/musicmania/`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: {
      VITE_SPOTIFY_CLIENT_ID: E2E_CLIENT_ID,
      VITE_SPOTIFY_REDIRECT_URI: `http://127.0.0.1:${PORT}/musicmania/auth/callback`,
      VITE_MUSICMANIA_API_URL: E2E_API_URL,
    },
  },
})
