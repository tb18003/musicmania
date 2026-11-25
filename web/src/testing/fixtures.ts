/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { PlayEvent } from '$lib/model/music'

/** Test data shared by unit tests (the web's core:testing TestData). */
export const play = (overrides: Partial<PlayEvent> = {}): PlayEvent => ({
  trackId: 't1',
  trackName: 'Low Tide',
  artistName: 'Coral Echo',
  albumName: 'Shoreline',
  imageUrl: null,
  playedAt: Date.UTC(2026, 10, 24, 12),
  msPlayed: 180_000,
  source: 'SYNC',
  skipped: null,
  shuffle: null,
  platform: null,
  ...overrides,
})
