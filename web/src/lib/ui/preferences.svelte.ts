/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { MessageKey } from './i18n/en'
import { detectLocale, translate, translatePlural, type Locale, type PluralBase } from './i18n/translate'

export type ThemePreference = 'system' | 'light' | 'dark'
export type LanguagePreference = 'system' | Locale
export type MotionPreference = 'system' | 'reduce' | 'full'

const STORAGE_KEY = 'musicmania.preferences'

interface Stored {
  theme: ThemePreference
  language: LanguagePreference
  motion: MotionPreference
}

const DEFAULTS: Stored = { theme: 'system', language: 'system', motion: 'system' }

const isOneOf = <T extends string>(value: unknown, options: readonly T[]): value is T =>
  typeof value === 'string' && (options as readonly string[]).includes(value)

/** Parses what was stored, ignoring anything unknown or malformed (it is user-controlled storage). */
export function parseStored(raw: string | null): Stored {
  if (!raw) return DEFAULTS
  try {
    const value: unknown = JSON.parse(raw)
    if (typeof value !== 'object' || value === null) return DEFAULTS
    const record = value as Record<string, unknown>
    return {
      theme: isOneOf(record.theme, ['system', 'light', 'dark'] as const) ? record.theme : DEFAULTS.theme,
      language: isOneOf(record.language, ['system', 'en', 'es'] as const) ? record.language : DEFAULTS.language,
      motion: isOneOf(record.motion, ['system', 'reduce', 'full'] as const) ? record.motion : DEFAULTS.motion,
    }
  } catch {
    return DEFAULTS
  }
}

export interface Environment {
  /** Browser storage; may be missing or throw (private mode, blocked storage). */
  storage: Pick<Storage, 'getItem' | 'setItem'> | undefined
  languages: readonly string[]
  prefersReducedMotion: () => boolean
}

/**
 * UI preferences (theme, language, animations). Only these harmless choices live in
 * localStorage; tokens and listening data never do.
 */
export class Preferences {
  theme = $state<ThemePreference>(DEFAULTS.theme)
  language = $state<LanguagePreference>(DEFAULTS.language)
  motion = $state<MotionPreference>(DEFAULTS.motion)

  readonly #env: Environment

  /** The language in use: the user's choice, or the first supported system language. */
  get locale(): Locale {
    return this.language === 'system' ? detectLocale(this.#env.languages) : this.language
  }

  constructor(env: Environment) {
    this.#env = env
    let raw: string | null = null
    try {
      raw = env.storage?.getItem(STORAGE_KEY) ?? null
    } catch {
      // Storage blocked: run with the defaults.
    }
    const stored = parseStored(raw)
    this.theme = stored.theme
    this.language = stored.language
    this.motion = stored.motion
  }

  /** Whether animations should be skipped right now (the user's choice wins over the system). */
  get reduceMotion(): boolean {
    return this.motion === 'system' ? this.#env.prefersReducedMotion() : this.motion === 'reduce'
  }

  t = (key: MessageKey, params?: Record<string, string | number>): string => translate(this.locale, key, params)

  /** Plural-aware string: `count` is also available as the {count} placeholder. */
  tp = (base: PluralBase, count: number, params?: Record<string, string | number>): string =>
    translatePlural(this.locale, base, count, params)

  setTheme(theme: ThemePreference) {
    this.theme = theme
    this.#save()
  }

  setLanguage(language: LanguagePreference) {
    this.language = language
    this.#save()
  }

  setMotion(motion: MotionPreference) {
    this.motion = motion
    this.#save()
  }

  #save() {
    const value: Stored = { theme: this.theme, language: this.language, motion: this.motion }
    try {
      this.#env.storage?.setItem(STORAGE_KEY, JSON.stringify(value))
    } catch {
      // Not persisted this time; the choice still applies until the app is closed.
    }
  }
}

/** Reflects the preferences on <html> so CSS tokens and assistive technologies follow them. */
export function applyToDocument(preferences: Preferences, root: HTMLElement) {
  root.dataset.theme = preferences.theme
  root.dataset.reduceMotion = String(preferences.reduceMotion)
  root.lang = preferences.locale
}
