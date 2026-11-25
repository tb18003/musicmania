/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Every first-party source file starts with the GPL header (the web's equivalent of spotlessApply).
// Usage: node scripts/check-license.mjs [--fix]
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { extname, join, relative } from 'node:path'

const ROOT = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const SKIP = new Set(['schema.d.ts'])
const year = new Date().getFullYear()
const lines = (prefix, open, close) =>
  [
    open,
    `${prefix}MusicMania - open-source Spotify listening statistics for the web.`,
    `${prefix}Copyright (C) ${year} MusicMania contributors`,
    `${prefix}SPDX-License-Identifier: GPL-3.0-or-later`,
    close,
  ].join('\n')

const HEADERS = {
  '.ts': lines(' * ', '/*', ' */'),
  '.js': lines(' * ', '/*', ' */'),
  '.mjs': lines(' * ', '/*', ' */'),
  '.css': lines(' * ', '/*', ' */'),
  '.svelte': lines('  ', '<!--', '-->'),
}
const SPDX = /SPDX-License-Identifier: GPL-3\.0-or-later/

function* files(path) {
  if (statSync(path).isDirectory()) {
    for (const entry of readdirSync(path)) yield* files(join(path, entry))
  } else if (HEADERS[extname(path)] && !SKIP.has(path.split(/[\\/]/).pop())) {
    yield path
  }
}

// Source folders plus every source file at the root of web/ (configs, root-level tests).
const rootFiles = readdirSync(ROOT).filter((name) => HEADERS[extname(name)] && statSync(join(ROOT, name)).isFile())
const SCAN = ['src', 'scripts', 'e2e', ...rootFiles]

const fix = process.argv.includes('--fix')
const missing = []
for (const start of SCAN) {
  let exists = true
  try {
    statSync(join(ROOT, start))
  } catch {
    exists = false
  }
  if (!exists) continue
  for (const file of files(join(ROOT, start))) {
    const text = readFileSync(file, 'utf8')
    // The header must be at the very top (after a shebang, if any), not anywhere in the file.
    const head = text.replace(/^#!.*\n/, '').slice(0, 400)
    if (SPDX.test(head)) continue
    if (fix) {
      writeFileSync(file, `${HEADERS[extname(file)]}\n\n${text}`)
    } else {
      missing.push(relative(ROOT, file))
    }
  }
}

if (missing.length > 0) {
  console.error(`Missing GPL header (run "npm run format"):\n  ${missing.join('\n  ')}`)
  process.exit(1)
}
