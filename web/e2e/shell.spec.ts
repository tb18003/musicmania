/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { expect, test, type Page } from '@playwright/test'

/** Fails the test on any console error, which includes Content Security Policy violations. */
function collectErrors(page: Page): string[] {
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  return errors
}

test('the app shell loads under the production CSP without errors', async ({ page }) => {
  const errors = collectErrors(page)
  await page.goto('./')

  await expect(page.getByRole('heading', { name: 'MusicMania' })).toBeVisible()
  const csp = await page.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute('content')
  expect(csp).toContain("script-src 'self'")
  expect(errors).toEqual([])
})

test('the install guide and settings are reachable, and the language can change', async ({ page }) => {
  await page.goto('./')
  await page.getByRole('button', { name: 'How to install' }).click()
  await expect(page.getByRole('heading', { name: 'Install MusicMania' })).toBeVisible()
  await page.getByRole('button', { name: 'Back' }).click()

  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByText('Español').click()
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'es')

  // The choice survives a reload (it is a harmless UI preference in localStorage).
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible()
})

test('the manifest makes it installable from the home screen', async ({ page, request }) => {
  await page.goto('./')
  const href = await page.locator('link[rel="manifest"]').getAttribute('href')
  expect(href).toBe('/musicmania/manifest.webmanifest')

  const manifest = await (await request.get(href ?? '')).json()
  expect(manifest).toMatchObject({ name: 'MusicMania', display: 'standalone', start_url: './' })
  for (const icon of manifest.icons as { src: string }[]) {
    expect((await request.get(`/musicmania/${icon.src}`)).ok(), icon.src).toBe(true)
  }
  expect((await request.get('/musicmania/icons/apple-touch-icon.png')).ok()).toBe(true)
})

test('the Android sign-in fallback page is still served, without any script', async ({ request }) => {
  const response = await request.get('/musicmania/callback.html')
  expect(response.ok()).toBe(true)
  const html = await response.text()
  expect(html).toContain('This page should have opened in the MusicMania app')
  expect(html).not.toContain('<script')
})
