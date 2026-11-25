/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import InstallGuide from './InstallGuide.svelte'

const context = () =>
  uiContextFor(new Preferences({ storage: undefined, languages: ['en'], prefersReducedMotion: () => true }))

describe('InstallGuide', () => {
  it('shows the visitor platform first', () => {
    render(InstallGuide, { props: { platform: 'android', installed: false, onback: vi.fn() }, context: context() })
    const titles = screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)
    expect(titles.slice(0, 2)).toEqual(['Android (Chrome)', 'iPhone and iPad (Safari)'])
  })

  it('lists the four iPhone steps, starting with Safari', () => {
    render(InstallGuide, { props: { platform: 'ios', installed: false, onback: vi.fn() }, context: context() })
    const steps = screen.getAllByRole('listitem').map((item) => item.textContent)
    expect(steps[0]).toBe('Open this page in Safari.')
    expect(steps).toHaveLength(6)
  })

  it('tells installed users they are already set', () => {
    render(InstallGuide, { props: { platform: 'ios', installed: true, onback: vi.fn() }, context: context() })
    expect(screen.getByRole('status').textContent).toBe('You are already using the installed app.')
  })
})
