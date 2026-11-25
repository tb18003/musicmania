/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Runs the import off the main thread: unzipping and parsing years of history never freezes the UI.
import type { DataResult } from '$lib/model/result'
import type { ImportProgress } from '$lib/model/top'
import { MusicManiaDatabase } from '../db/database'
import { DexieHistoryRepository } from '../db/repositories'
import { importStreamingHistory } from './importer'

export type ImporterMessage =
  { type: 'progress'; progress: ImportProgress } | { type: 'done'; result: DataResult<ImportProgress> }

interface WorkerScope {
  onmessage: ((event: MessageEvent<File[]>) => void) | null
  postMessage(message: ImporterMessage): void
}
const scope = self as unknown as WorkerScope

scope.onmessage = async (event) => {
  const db = new MusicManiaDatabase()
  const history = new DexieHistoryRepository(db)
  try {
    const result = await importStreamingHistory(
      event.data,
      (plays) => history.addPlays(plays),
      (progress) => scope.postMessage({ type: 'progress', progress }),
    )
    scope.postMessage({ type: 'done', result })
  } catch {
    scope.postMessage({ type: 'done', result: { ok: false, error: { kind: 'unknown' } } })
  } finally {
    db.close()
  }
}
