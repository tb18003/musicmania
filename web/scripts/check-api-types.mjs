/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Fails when src/lib/data/cloud/schema.d.ts is out of date with api/openapi.yaml,
// so the web client can never drift from the API contract. Fix with "npm run api:types".
import { readFileSync } from 'node:fs'
import openapiTS, { astToString } from 'openapi-typescript'

const spec = new URL('../../api/openapi.yaml', import.meta.url)
const committed = new URL('../src/lib/data/cloud/schema.d.ts', import.meta.url)

let generated
try {
  generated = astToString(await openapiTS(spec))
} catch (error) {
  console.error(`api/openapi.yaml is not a valid OpenAPI document: ${error instanceof Error ? error.message : error}`)
  process.exit(1)
}
const normalise = (text) => text.replace(/\r\n/g, '\n').trim()
let current = ''
try {
  current = readFileSync(committed, 'utf8')
} catch {
  // A missing file is reported below as out of date.
}

// The CLI adds a banner comment before the generated declarations; compare from the first declaration.
const body = (text) => normalise(text).slice(normalise(text).indexOf('export '))
if (body(current) !== body(generated)) {
  console.error('src/lib/data/cloud/schema.d.ts is out of date with api/openapi.yaml: run "npm run api:types".')
  process.exit(1)
}
