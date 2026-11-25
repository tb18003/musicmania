/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { Unzip, UnzipInflate } from 'fflate'
import type { ImportFile } from '$lib/domain/repositories'
import type { PlayEvent } from '$lib/model/music'
import { failure, success, type AppError, type DataResult } from '$lib/model/result'
import type { ImportProgress } from '$lib/model/top'
import { isHistoryFile, toPlayEvent } from './parser'

const MIB = 1024 * 1024

/**
 * Zip-bomb and abuse guards (security rule 8), like ImportLimits on Android. Each JSON file is
 * parsed in memory on the web, so its cap is lower than the app's streamed 256 MiB.
 */
export interface ImportLimits {
  maxZipEntries: number
  maxEntryBytes: number
  maxTotalBytes: number
}

export const DEFAULT_LIMITS: ImportLimits = {
  maxZipEntries: 2_000,
  maxEntryBytes: 64 * MIB,
  maxTotalBytes: 4 * 1024 * MIB,
}

const BATCH_SIZE = 1_000
const ZIP_MAGIC = [0x50, 0x4b, 0x03, 0x04]

class ImportFailure extends Error {
  constructor(readonly error: AppError) {
    super(error.kind)
  }
}

const invalid = (detail: string) => new ImportFailure({ kind: 'invalidData', detail })

interface Counters extends ImportProgress {
  files: number
  read: number
  inserted: number
  skipped: number
  earliest: number | null
  latest: number | null
  totalBytes: number
}

/**
 * Imports Spotify data exports (the .zip straight from Spotify, or extracted .json files) on the
 * device. Archives are streamed (never extracted to storage); only history files are inflated,
 * with per-file, total and entry-count limits. Plays go to `sink` in batches; it returns how many
 * were new (duplicates of synced or previously imported plays are ignored).
 */
export async function importStreamingHistory(
  files: readonly ImportFile[],
  sink: (plays: readonly PlayEvent[]) => Promise<number>,
  onProgress: (progress: ImportProgress) => void,
  limits: ImportLimits = DEFAULT_LIMITS,
): Promise<DataResult<ImportProgress>> {
  const counters: Counters = { files: 0, read: 0, inserted: 0, skipped: 0, earliest: null, latest: null, totalBytes: 0 }
  const report = () => onProgress(snapshot(counters))
  try {
    for (const file of files) await importFile(file, counters, sink, report, limits)
    if (counters.files === 0) throw invalid('No streaming history files found')
    return success(snapshot(counters))
  } catch (error) {
    if (error instanceof ImportFailure) return failure(error.error)
    throw error
  }
}

const snapshot = ({ totalBytes: _, ...progress }: Counters): ImportProgress => ({ ...progress })

async function importFile(
  file: ImportFile,
  counters: Counters,
  sink: (plays: readonly PlayEvent[]) => Promise<number>,
  report: () => void,
  limits: ImportLimits,
) {
  const reader = file.stream().getReader()
  const first = await reader.read()
  if (first.done) return
  const isZip = ZIP_MAGIC.every((byte, index) => first.value[index] === byte)

  if (!isZip) {
    if (file.size > limits.maxEntryBytes) throw invalid(`${file.name} is too large`)
    await reader.cancel()
    countBytes(counters, file.size, limits)
    await importJson(file.name, await file.text(), counters, sink, report)
    return
  }

  const completed: { name: string; parts: Uint8Array[] }[] = []
  let problem: ImportFailure | null = null
  let entries = 0
  const unzip = new Unzip((entry) => {
    if (++entries > limits.maxZipEntries) {
      problem ??= invalid('Too many files in archive')
      return
    }
    // Files that are not history are never started, so never inflated.
    if (!isHistoryFile(entry.name)) return
    const parts: Uint8Array[] = []
    let size = 0
    entry.ondata = (error, chunk, final) => {
      if (problem) return
      if (error) {
        problem = invalid('Corrupted archive')
        return
      }
      size += chunk.length
      try {
        if (size > limits.maxEntryBytes) throw invalid(`${entry.name} is too large`)
        countBytes(counters, chunk.length, limits)
      } catch (failureError) {
        problem = failureError as ImportFailure
        entry.terminate()
        return
      }
      parts.push(chunk)
      if (final) completed.push({ name: entry.name, parts })
    }
    entry.start()
  })
  unzip.register(UnzipInflate)

  let chunk: ReadableStreamReadResult<Uint8Array> = first
  while (!chunk.done) {
    const next = await reader.read()
    unzip.push(chunk.value, next.done)
    if (problem) {
      await reader.cancel()
      throw problem
    }
    // Parse finished entries between chunks: memory holds at most one file's text at a time.
    for (const entry of completed.splice(0)) {
      const name = entry.name.split('/').pop() ?? entry.name
      await importJson(name, decode(entry.parts), counters, sink, report)
    }
    chunk = next
  }
}

function countBytes(counters: Counters, bytes: number, limits: ImportLimits) {
  counters.totalBytes += bytes
  if (counters.totalBytes > limits.maxTotalBytes) throw invalid('The export is too large')
}

function decode(parts: Uint8Array[]): string {
  const decoder = new TextDecoder()
  return parts.map((part, index) => decoder.decode(part, { stream: index < parts.length - 1 })).join('')
}

async function importJson(
  name: string,
  json: string,
  counters: Counters,
  sink: (plays: readonly PlayEvent[]) => Promise<number>,
  report: () => void,
) {
  let entries: unknown
  try {
    entries = JSON.parse(json)
  } catch {
    throw invalid(`${name} is not a Spotify streaming history file`)
  }
  if (!Array.isArray(entries)) throw invalid(`${name} is not a Spotify streaming history file`)
  counters.files++
  report()
  for (let start = 0; start < entries.length; start += BATCH_SIZE) {
    const plays: PlayEvent[] = []
    for (const raw of entries.slice(start, start + BATCH_SIZE)) {
      const play = toPlayEvent(raw)
      if (play) plays.push(play)
      else counters.skipped++
    }
    counters.read += plays.length
    for (const play of plays) {
      if (counters.earliest === null || play.playedAt < counters.earliest) counters.earliest = play.playedAt
      if (counters.latest === null || play.playedAt > counters.latest) counters.latest = play.playedAt
    }
    counters.inserted += await sink(plays)
    report()
  }
}
