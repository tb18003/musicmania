/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Calendar dates in the device's time zone, as "YYYY-MM-DD" strings (the web has no LocalDate
 * until Temporal ships everywhere). Ranges are half-open [start, end) in epoch millis.
 */
export type LocalDate = string

export interface DateRange {
  readonly start: number
  readonly end: number
}

const pad = (value: number) => String(value).padStart(2, '0')

export function localDateOf(epochMs: number): LocalDate {
  const date = new Date(epochMs)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function parts(date: LocalDate): [number, number, number] {
  const [year, month, day] = date.split('-').map(Number)
  if (year === undefined || month === undefined || day === undefined) throw new Error(`Invalid date ${date}`)
  return [year, month, day]
}

/** Local midnight of a date, in epoch millis (DST-safe: built from calendar fields). */
export function startOfDay(date: LocalDate): number {
  const [year, month, day] = parts(date)
  return new Date(year, month - 1, day).getTime()
}

export function addDays(date: LocalDate, days: number): LocalDate {
  const [year, month, day] = parts(date)
  return localDateOf(new Date(year, month - 1, day + days).getTime())
}

/** Monday of the week containing the date (weeks start on Monday, like LeaderboardPeriod.WEEK). */
export function weekStart(date: LocalDate): LocalDate {
  const [year, month, day] = parts(date)
  const weekday = new Date(year, month - 1, day).getDay() // 0 = Sunday
  return addDays(date, -((weekday + 6) % 7))
}

export const dayRange = (date: LocalDate): DateRange => ({ start: startOfDay(date), end: startOfDay(addDays(date, 1)) })

export const weekRange = (date: LocalDate): DateRange => {
  const monday = weekStart(date)
  return { start: startOfDay(monday), end: startOfDay(addDays(monday, 7)) }
}
