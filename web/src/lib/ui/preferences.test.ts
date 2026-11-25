/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { applyToDocument, parseStored, Preferences, type Environment } from './preferences.svelte'

class MemoryStorage {
  readonly data = new Map<string, string>()
  getItem = (key: string) => this.data.get(key) ?? null
  setItem = (key: string, value: string) => void this.data.set(key, value)
}

const env = (overrides: Partial<Environment> = {}): Environment => ({
  storage: new MemoryStorage(),
  languages: ['es-ES'],
  prefersReducedMotion: () => false,
  ...overrides,
})

describe('Preferences', () => {
  it('follows the system by default', () => {
    const preferences = new Preferences(env())
    expect(preferences.theme).toBe('system')
    expect(preferences.locale).toBe('es')
    expect(preferences.reduceMotion).toBe(false)
    expect(preferences.t('settings_title')).toBe('Ajustes')
  })

  it('persists choices and restores them on the next launch', () => {
    const storage = new MemoryStorage()
    const first = new Preferences(env({ storage }))
    first.setTheme('light')
    first.setLanguage('en')
    first.setMotion('reduce')

    const second = new Preferences(env({ storage }))
    expect(second.theme).toBe('light')
    expect(second.locale).toBe('en')
    expect(second.reduceMotion).toBe(true)
  })

  it('the user choice wins over the system reduced-motion setting', () => {
    const preferences = new Preferences(env({ prefersReducedMotion: () => true }))
    expect(preferences.reduceMotion).toBe(true)
    preferences.setMotion('full')
    expect(preferences.reduceMotion).toBe(false)
  })

  it('keeps working when storage is blocked', () => {
    const broken = {
      getItem: () => {
        throw new Error('SecurityError')
      },
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
    }
    const preferences = new Preferences(env({ storage: broken }))
    expect(() => preferences.setTheme('dark')).not.toThrow()
    expect(preferences.theme).toBe('dark')
  })

  it('ignores tampered or malformed stored values', () => {
    expect(parseStored('{"theme":"neon","language":"fr","motion":1}')).toEqual({
      theme: 'system',
      language: 'system',
      motion: 'system',
    })
    expect(parseStored('not json').theme).toBe('system')
    expect(parseStored('null').theme).toBe('system')
  })

  it('reflects the preferences on the document', () => {
    const preferences = new Preferences(env())
    preferences.setTheme('dark')
    preferences.setMotion('reduce')
    applyToDocument(preferences, document.documentElement)
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.dataset.reduceMotion).toBe('true')
    expect(document.documentElement.lang).toBe('es')
  })
})
