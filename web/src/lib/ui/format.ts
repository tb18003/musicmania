/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { AppError } from '$lib/model/result'
import type { MessageKey } from './i18n/en'
import type { Locale } from './i18n/translate'

/** Whole minutes, grouped for the locale ("1,234" / "1.234"). */
export function formatMinutes(ms: number, locale: Locale): string {
  return new Intl.NumberFormat(locale).format(Math.round(ms / 60_000))
}

export function formatNumber(value: number, locale: Locale): string {
  return new Intl.NumberFormat(locale).format(value)
}

/** "5 minutes ago" / "hace 5 minutos". */
export function formatRelative(epochMs: number, now: number, locale: Locale): string {
  const seconds = Math.round((epochMs - now) / 1000)
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  const steps: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86_400],
    ['hour', 3_600],
    ['minute', 60],
  ]
  for (const [unit, size] of steps) {
    if (Math.abs(seconds) >= size) return format.format(Math.round(seconds / size), unit)
  }
  return format.format(0, 'second')
}

export function formatTime(epochMs: number, locale: Locale): string {
  return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' }).format(epochMs)
}

/** "Wednesday, 25 November" for headers further back than yesterday. */
export function formatDay(epochMs: number, locale: Locale): string {
  return new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long' }).format(epochMs)
}

/** The message shown for an error; never includes technical details. */
export function errorMessageKey(error: AppError): MessageKey {
  switch (error.kind) {
    case 'network':
      return 'error_network'
    case 'forbidden':
      return 'error_forbidden'
    case 'rateLimited':
      return 'error_rate_limited'
    default:
      return 'error_generic'
  }
}
