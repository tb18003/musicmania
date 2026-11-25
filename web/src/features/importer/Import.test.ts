/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import type { ImportFile, StreamingHistoryImporter } from '$lib/domain/repositories'
import { failure, success } from '$lib/model/result'
import type { ImportProgress } from '$lib/model/top'
import { uiContextFor } from '$lib/ui/context'
import { Preferences } from '$lib/ui/preferences.svelte'
import ImportScreen from './ImportScreen.svelte'
import { ImportViewModel, type ImportState } from './ImportViewModel.svelte'

const DONE: ImportProgress = {
  files: 3,
  read: 12_345,
  inserted: 12_000,
  skipped: 7,
  earliest: Date.UTC(2019, 0, 5),
  latest: Date.UTC(2026, 10, 1),
}

const importer = (result = success(DONE)): StreamingHistoryImporter & { files: readonly ImportFile[][] } => {
  const files: ImportFile[][] = []
  return {
    files,
    async import(picked, onProgress) {
      files.push([...picked])
      onProgress({ ...DONE, read: 1_000 })
      return result
    },
  }
}

describe('ImportViewModel', () => {
  it('reports progress, the summary, and lets the app recompute', async () => {
    const onImported = vi.fn()
    const source = importer()
    const vm = new ImportViewModel(source, onImported)

    await vm.start([new File(['[]'], 'my_spotify_data.zip')])

    expect(vm.state).toEqual({ status: 'done', progress: DONE, error: null })
    expect(onImported).toHaveBeenCalledOnce()
    expect(source.files[0]?.map((f) => f.name)).toEqual(['my_spotify_data.zip'])
  })

  it('a failed import keeps the error and does not refresh anything', async () => {
    const onImported = vi.fn()
    const vm = new ImportViewModel(
      importer(failure({ kind: 'invalidData', detail: 'Too many files in archive' })),
      onImported,
    )
    await vm.start([new File(['x'], 'x.zip')])
    expect(vm.state.status).toBe('error')
    expect(onImported).not.toHaveBeenCalled()
  })

  it('choosing nothing does nothing', async () => {
    const source = importer()
    await new ImportViewModel(source, vi.fn()).start([])
    expect(source.files).toHaveLength(0)
  })
})

describe('ImportScreen', () => {
  const context = () =>
    uiContextFor(new Preferences({ storage: undefined, languages: ['es'], prefersReducedMotion: () => true }))
  const render_ = (state: ImportState, onfiles = vi.fn()) =>
    render(ImportScreen, { props: { state, onfiles, onback: vi.fn() }, context: context() })

  it('explains how to get the export and accepts ZIP or JSON files', async () => {
    const onfiles = vi.fn()
    render_({ status: 'idle', progress: null, error: null }, onfiles)
    expect(screen.getAllByRole('listitem')).toHaveLength(4)
    const input = screen.getByLabelText('Elegir archivos ZIP o JSON') as HTMLInputElement
    expect(input.accept).toBe('.zip,.json,application/zip,application/json')
    await fireEvent.change(input, { target: { files: [new File(['[]'], 'a.zip')] } })
    expect(onfiles).toHaveBeenCalledOnce()
  })

  it('shows the summary with localised numbers and dates', () => {
    render_({ status: 'done', progress: DONE, error: null })
    expect(screen.getByText('3 archivos · 12.345 escuchas · 12.000 nuevas')).toBeTruthy()
    expect(screen.getByText(/omitidas/).textContent).toContain('7')
  })

  it('explains invalid exports', () => {
    render_({ status: 'error', progress: null, error: { kind: 'invalidData', detail: 'Too many files in archive' } })
    expect(screen.getByRole('alert').textContent).toContain('Too many files in archive')
  })
})
