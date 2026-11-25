/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { ImportFile, StreamingHistoryImporter } from '$lib/domain/repositories'
import type { DataResult } from '$lib/model/result'
import type { ImportProgress } from '$lib/model/top'
import type { ImporterMessage } from './importer.worker'

/** The import, in a dedicated worker (one per import, terminated when done). */
export class WorkerStreamingHistoryImporter implements StreamingHistoryImporter {
  import(
    files: readonly ImportFile[],
    onProgress: (progress: ImportProgress) => void,
  ): Promise<DataResult<ImportProgress>> {
    return new Promise((resolve) => {
      const worker = new Worker(new URL('./importer.worker.ts', import.meta.url), { type: 'module' })
      worker.onmessage = (event: MessageEvent<ImporterMessage>) => {
        if (event.data.type === 'progress') {
          onProgress(event.data.progress)
          return
        }
        worker.terminate()
        resolve(event.data.result)
      }
      worker.onerror = () => {
        worker.terminate()
        resolve({ ok: false, error: { kind: 'unknown' } })
      }
      worker.postMessage(files)
    })
  }
}
