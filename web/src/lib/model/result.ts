/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** Typed failures, like AppError in core:common. Messages never contain tokens or codes. */
export type AppError =
  | { kind: 'network' }
  | { kind: 'unauthorized' }
  | { kind: 'sessionExpired' }
  | { kind: 'forbidden' }
  | { kind: 'notAllowlisted' }
  | { kind: 'rateLimited'; retryAfterSeconds: number | null }
  | { kind: 'notFound' }
  | { kind: 'invalidData'; detail: string }
  | { kind: 'server'; status: number }
  | { kind: 'unknown' }

export type DataResult<T> = { ok: true; data: T } | { ok: false; error: AppError }

export const success = <T>(data: T): DataResult<T> => ({ ok: true, data })
export const failure = <T = never>(error: AppError): DataResult<T> => ({ ok: false, error })
