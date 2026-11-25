/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import js from '@eslint/js'
import svelte from 'eslint-plugin-svelte'
import globals from 'globals'
import ts from 'typescript-eslint'
import svelteConfig from './svelte.config.js'

/**
 * Layer rules, the web's equivalent of the Konsist tests in mobile/app/src/test:
 * model ← domain ← data; features only see domain, model and ui; app wires everything.
 */
const layer = (files, forbidden, message) => ({
  files,
  rules: {
    'no-restricted-imports': ['error', { patterns: forbidden.map((group) => ({ group: [group], message })) }],
  },
})

export default ts.config(
  { ignores: ['dist/', 'dev-dist/', 'coverage/', 'test-results/', 'playwright-report/', '**/schema.d.ts'] },
  js.configs.recommended,
  ...ts.configs.strict,
  ...svelte.configs.recommended,
  {
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    rules: {
      'no-console': ['error', { allow: ['error'] }],
      eqeqeq: 'error',
      '@typescript-eslint/no-non-null-assertion': 'error',
      '@typescript-eslint/consistent-type-imports': 'error',
    },
  },
  {
    files: ['**/*.svelte', '**/*.svelte.ts'],
    languageOptions: { parserOptions: { parser: ts.parser, extraFileExtensions: ['.svelte'], svelteConfig } },
  },
  layer(
    ['src/lib/model/**'],
    ['$lib/domain/*', '$lib/data/*', '$lib/ui/*', '$features/*', '$app/*', 'dexie', 'fflate'],
    'The model is pure: it depends on nothing.',
  ),
  layer(
    ['src/lib/domain/**'],
    ['$lib/data/*', '$lib/ui/*', '$features/*', '$app/*', 'dexie', 'fflate', 'svelte', 'svelte/*'],
    'The domain is pure TypeScript: no storage, network, UI or framework.',
  ),
  layer(
    ['src/lib/data/**'],
    ['$lib/ui/*', '$features/*', '$app/*'],
    'Data implements domain interfaces; it never imports UI.',
  ),
  layer(
    ['src/lib/ui/**'],
    ['$lib/data/*', '$features/*', '$app/*', 'dexie', 'fflate'],
    'The design system knows nothing about data or features.',
  ),
  layer(
    ['src/features/**'],
    ['$lib/data/*', '$app/*', 'dexie', 'fflate'],
    'Features only depend on domain, model and ui. Wire data in src/app.',
  ),
)
