/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { HistoryRepository } from '$lib/domain/repositories'

/**
 * Saves the whole local history as JSON (the user's own data, e.g. to move devices or keep a
 * backup in case iOS clears website data). Nothing is sent anywhere: it is a local download.
 */
export async function exportHistory(
  history: HistoryRepository,
  now: number,
  doc: Document = document,
): Promise<number> {
  const plays = await history.playsBetween(0, Number.MAX_SAFE_INTEGER)
  const blob = new Blob([JSON.stringify(plays, null, 1)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = doc.createElement('a')
  link.href = url
  link.download = `musicmania-history-${new Date(now).toISOString().slice(0, 10)}.json`
  doc.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
  return plays.length
}
