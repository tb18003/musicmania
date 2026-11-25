/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { en, type MessageKey, type Messages } from './en'
import { es } from './es'

export type Locale = 'en' | 'es'

const catalogs: Record<Locale, Messages> = { en, es }

/** First supported language in the user's preference list; English otherwise. */
export function detectLocale(languages: readonly string[]): Locale {
  for (const language of languages) {
    const base = language.toLowerCase().split('-')[0]
    if (base === 'es' || base === 'en') return base
  }
  return 'en'
}

/** Looks up a string and fills its {placeholders}. Unknown placeholders are left visible. */
export function translate(locale: Locale, key: MessageKey, params: Record<string, string | number> = {}): string {
  return catalogs[locale][key].replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  )
}

/** Keys that come in `_one` / `_other` pairs, addressed by their common base. */
export type PluralBase = {
  [K in MessageKey]: K extends `${infer Base}_one` ? (`${Base}_other` extends MessageKey ? Base : never) : never
}[MessageKey]

/** Picks the plural form with the locale's rules (CLDR); anything but "one" uses "other". */
export function translatePlural(
  locale: Locale,
  base: PluralBase,
  count: number,
  params: Record<string, string | number> = {},
): string {
  const form = new Intl.PluralRules(locale).select(count) === 'one' ? 'one' : 'other'
  return translate(locale, `${base}_${form}` as MessageKey, { count, ...params })
}
