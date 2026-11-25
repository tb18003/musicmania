/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it } from 'vitest'
import { en } from './en'
import { es } from './es'
import { detectLocale, translate } from './translate'

describe('translate', () => {
  it('every English key has a Spanish translation and vice versa', () => {
    expect(Object.keys(es).sort()).toEqual(Object.keys(en).sort())
  })

  it('keeps the same placeholders in both languages', () => {
    const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort()
    for (const key of Object.keys(en) as (keyof typeof en)[]) {
      expect(placeholders(es[key]), key).toEqual(placeholders(en[key]))
    }
  })

  it('fills placeholders and leaves unknown ones visible', () => {
    expect(translate('es', 'settings_version', { version: '0.1.0' })).toBe('Versión 0.1.0')
    expect(translate('en', 'settings_version')).toBe('Version {version}')
  })

  it('picks the first supported language, English otherwise', () => {
    expect(detectLocale(['es-ES', 'en-US'])).toBe('es')
    expect(detectLocale(['fr-FR', 'en-GB'])).toBe('en')
    expect(detectLocale(['de-DE'])).toBe('en')
    expect(detectLocale([])).toBe('en')
  })
})
