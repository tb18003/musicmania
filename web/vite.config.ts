/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fileURLToPath, URL } from 'node:url'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { svelteTesting } from '@testing-library/svelte/vite'
import { defineConfig, loadEnv, type Plugin } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import { contentSecurityPolicy } from './csp.config.ts'
import pkg from './package.json' with { type: 'json' }

/** Served from the project GitHub Pages site: https://<user>.github.io/musicmania/ */
const BASE = '/musicmania/'

/** Adds the CSP meta tag to built pages only (the dev server needs inline scripts for HMR). */
function csp(apiUrl: string | undefined): Plugin {
  return {
    name: 'musicmania-csp',
    apply: 'build',
    transformIndexHtml: {
      order: 'pre',
      handler: () => [
        {
          tag: 'meta',
          attrs: { 'http-equiv': 'Content-Security-Policy', content: contentSecurityPolicy(apiUrl) },
          injectTo: 'head-prepend',
        },
      ],
    },
  }
}

const src = (path: string) => fileURLToPath(new URL(path, import.meta.url))

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiUrl = env.VITE_MUSICMANIA_API_URL || undefined
  if (apiUrl && !apiUrl.startsWith('https://')) throw new Error('VITE_MUSICMANIA_API_URL must be https (or empty)')

  return {
    base: BASE,
    define: { __APP_VERSION__: JSON.stringify(pkg.version) },
    resolve: {
      alias: {
        $lib: src('./src/lib'),
        $features: src('./src/features'),
        $app: src('./src/app'),
        $testing: src('./src/testing'),
      },
    },
    plugins: [
      svelte(),
      svelteTesting(),
      csp(apiUrl),
      VitePWA({
        injectRegister: 'script',
        registerType: 'autoUpdate',
        // Our own manifest in public/: one source of truth, readable without the build.
        manifest: false,
        workbox: {
          // Only the static app shell is cached. Spotify and API responses never are.
          globPatterns: ['**/*.{js,css,html,svg,png,ttf,webmanifest}'],
          globIgnores: ['callback.html', 'auth/**'],
          navigateFallback: null,
          runtimeCaching: [],
          cleanupOutdatedCaches: true,
        },
      }),
    ],
    build: {
      target: 'es2023',
      sourcemap: true,
      rollupOptions: {
        // The app, and the web sign-in redirect served at /musicmania/auth/callback.
        input: { main: src('./index.html'), callback: src('./auth/callback.html') },
      },
    },
    test: {
      environment: 'jsdom',
      include: ['src/**/*.test.ts', '*.test.ts'],
      setupFiles: ['src/test-setup.ts'],
      restoreMocks: true,
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,svelte}'],
        exclude: [
          'src/**/*.test.ts',
          'src/test-setup.ts',
          'src/testing/**',
          'src/lib/data/testing.ts',
          'src/main.ts',
          'src/**/*.d.ts',
        ],
        reporter: ['text-summary', 'html', 'lcov'],
      },
    },
  }
})
