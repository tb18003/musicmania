/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import Dexie, { type Table } from 'dexie'
import type { PlayEvent } from '$lib/model/music'

export interface PlayRow extends PlayEvent {
  id?: number
  dedupeKey: string
}

export interface KeyValue {
  key: string
  value: unknown
}

export const DATABASE_NAME = 'musicmania'

/**
 * The local single source of truth (like Room on Android). `plays` mirrors play_events, with the
 * same unique dedupe key and indexes; `state` holds sync cursors; `secrets` holds the encrypted
 * refresh token, its non-extractable key and the short-lived PKCE login state.
 */
export class MusicManiaDatabase extends Dexie {
  plays!: Table<PlayRow, number>
  state!: Table<KeyValue, string>
  secrets!: Table<KeyValue, string>

  constructor(name = DATABASE_NAME) {
    super(name)
    this.version(1).stores({
      plays: '++id, &dedupeKey, playedAt, artistName, [trackName+artistName]',
      state: 'key',
      secrets: 'key',
    })
  }
}
