/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { ImportFile, StreamingHistoryImporter } from '$lib/domain/repositories'
import type { AppError } from '$lib/model/result'
import type { ImportProgress } from '$lib/model/top'

export interface ImportState {
  status: 'idle' | 'running' | 'done' | 'error'
  progress: ImportProgress | null
  error: AppError | null
}

export class ImportViewModel {
  state = $state<ImportState>({ status: 'idle', progress: null, error: null })
  readonly #importer: StreamingHistoryImporter
  readonly #onImported: () => void

  /** `onImported` lets the rest of the app recompute (home, history, lifetime top). */
  constructor(importer: StreamingHistoryImporter, onImported: () => void) {
    this.#importer = importer
    this.#onImported = onImported
  }

  async start(files: readonly ImportFile[]): Promise<void> {
    if (files.length === 0 || this.state.status === 'running') return
    this.state = { status: 'running', progress: null, error: null }
    const result = await this.#importer.import(files, (progress) => {
      this.state.progress = progress
    })
    if (result.ok) {
      this.state = { status: 'done', progress: result.data, error: null }
      this.#onImported()
    } else {
      this.state = { status: 'error', progress: this.state.progress, error: result.error }
    }
  }
}
