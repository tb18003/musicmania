/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { expect, test } from '@playwright/test'
import { E2E_CLIENT_ID } from '../playwright.config'
import { mockSpotify } from './spotify-mock'

test('sign in with Spotify (PKCE), sync the last plays and see them, then sign out wiping everything', async ({
  page,
}) => {
  const spotify = await mockSpotify(page)
  const errors: string[] = []
  page.on('console', (message) => message.type() === 'error' && errors.push(message.text()))

  await page.goto('./')
  await page.getByRole('button', { name: 'Sign in with Spotify' }).click()

  // Back from Spotify: the code was exchanged with the verifier, and the URL no longer has it.
  await expect(page.getByRole('heading', { name: 'Hi, E2E Tester' })).toBeVisible()
  expect(page.url()).not.toContain('code=')
  const exchange = spotify.tokenRequests[0]
  expect(exchange?.get('grant_type')).toBe('authorization_code')
  expect(exchange?.get('client_id')).toBe(E2E_CLIENT_ID)
  expect(exchange?.get('code_verifier')).toMatch(/^[A-Za-z0-9\-._~]{64}$/)
  expect(exchange?.has('client_secret')).toBe(false)

  // Synced on open: three plays of 3:20 today = 10 minutes, 3 streams.
  await expect(page.getByText('Synced', { exact: false })).toBeVisible()
  await expect(page.getByText('3 streams').first()).toBeVisible()
  await expect(page.getByText('Coral Echo')).toBeVisible()

  await page.getByRole('button', { name: 'History' }).click()
  await expect(page.getByRole('heading', { name: 'Today' })).toBeVisible()
  await expect(page.getByText('Night Drive')).toBeVisible()

  // The bearer token only ever went to Spotify's API and the configured MusicMania API.
  expect([...spotify.authorizedHosts].sort()).toEqual(['api.musicmania.test', 'api.spotify.com'])
  expect(spotify.bearers.every((bearer) => bearer === 'Bearer e2e-access')).toBe(true)

  // The refresh token is encrypted in IndexedDB; nothing sensitive in localStorage.
  const local = await page.evaluate(() => JSON.stringify(window.localStorage))
  expect(local).not.toContain('e2e-refresh')
  expect(local).not.toContain('e2e-access')

  // Survives a reload: still signed in, history still there.
  await page.reload()
  await expect(page.getByText('Night Drive')).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('button', { name: 'Sign in with Spotify' })).toBeVisible()
  // The database was deleted; the app may reopen it empty, but nothing of the session remains.
  const left = await page.evaluate(
    () =>
      new Promise<number[]>((resolve, reject) => {
        const open = indexedDB.open('musicmania')
        open.onerror = () => reject(open.error)
        open.onsuccess = () => {
          const db = open.result
          const stores = ['plays', 'state', 'secrets'].filter((name) => db.objectStoreNames.contains(name))
          if (stores.length === 0) return resolve([])
          const tx = db.transaction(stores)
          const counts = stores.map((name) => tx.objectStore(name).count())
          tx.oncomplete = () => resolve(counts.map((request) => request.result as number))
        }
      }),
  )
  expect(left.every((count) => count === 0)).toBe(true)
  expect(errors).toEqual([])
})

test('a callback with a forged state is refused and explains what to do', async ({ page }) => {
  const spotify = await mockSpotify(page)
  await page.goto('./auth/callback?code=stolen&state=forged')
  await expect(page.getByRole('heading', { name: 'Sign-in did not finish' })).toBeVisible()
  expect(page.url()).not.toContain('stolen')
  expect(spotify.tokenRequests).toHaveLength(0)
  await page.getByRole('link', { name: 'Back to MusicMania' }).click()
  await expect(page.getByRole('button', { name: 'Sign in with Spotify' })).toBeVisible()
})
