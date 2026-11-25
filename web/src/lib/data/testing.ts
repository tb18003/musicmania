/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { MusicManiaDatabase } from './db/database'

let counter = 0

/** A fresh, isolated database per test (fake-indexeddb in Vitest). */
export const testDatabase = () => new MusicManiaDatabase(`musicmania-test-${++counter}-${Date.now()}`)

export interface RecordedRequest {
  url: string
  method: string
  headers: Headers
  body: string | null
}

/** A fake fetch that records every request and answers from a handler (an in-memory server). */
export function fakeFetch(handler: (request: RecordedRequest) => Response | Promise<Response>) {
  const requests: RecordedRequest[] = []
  const fetcher = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const request: RecordedRequest = {
      url: String(input instanceof Request ? input.url : input),
      method: init?.method ?? 'GET',
      headers: new Headers(init?.headers),
      body: init?.body === undefined || init.body === null ? null : String(init.body),
    }
    requests.push(request)
    return handler(request)
  }) as typeof fetch
  return { fetcher, requests }
}

export const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
