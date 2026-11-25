/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { addDays, dayRange, localDateOf, startOfDay, weekRange, weekStart } from './dates'

describe('local dates', () => {
  it('formats and parses local calendar dates', () => {
    expect(localDateOf(new Date(2026, 0, 5, 23, 59).getTime())).toBe('2026-01-05')
    expect(startOfDay('2026-01-05')).toBe(new Date(2026, 0, 5).getTime())
  })

  it('adds days across months, years and leap days', () => {
    expect(addDays('2026-01-31', 1)).toBe('2026-02-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('weeks start on Monday (LeaderboardPeriod.WEEK)', () => {
    expect(weekStart('2026-11-25')).toBe('2026-11-23') // Wednesday
    expect(weekStart('2026-11-23')).toBe('2026-11-23') // Monday
    expect(weekStart('2026-11-29')).toBe('2026-11-23') // Sunday
  })

  it('ranges are half-open and a week is seven local days', () => {
    const day = dayRange('2026-11-25')
    expect(day.end).toBe(startOfDay('2026-11-26'))
    const week = weekRange('2026-11-25')
    expect(week.start).toBe(startOfDay('2026-11-23'))
    expect(week.end).toBe(startOfDay('2026-11-30'))
  })
})
