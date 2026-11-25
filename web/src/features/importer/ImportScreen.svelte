<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import Card from '$lib/ui/components/Card.svelte'
  import Notice from '$lib/ui/components/Notice.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import { useUi } from '$lib/ui/context'
  import { formatNumber } from '$lib/ui/format'
  import { entrances } from '$lib/ui/motion'
  import type { ImportState } from './ImportViewModel.svelte'

  interface Props {
    state: ImportState
    onfiles: (files: File[]) => void
    onback: () => void
  }

  let { state, onfiles, onback }: Props = $props()
  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('import')
  const steps = ['import_how_step_1', 'import_how_step_2', 'import_how_step_3', 'import_how_step_4'] as const
  const number = (value: number) => formatNumber(value, ui.locale)
  const date = (epochMs: number) => new Intl.DateTimeFormat(ui.locale, { dateStyle: 'medium' }).format(epochMs)

  function picked(event: Event) {
    const input = event.currentTarget as HTMLInputElement
    onfiles([...(input.files ?? [])])
    input.value = ''
  }
</script>

<main>
  <ScreenHeader
    title={ui.t('import_title')}
    subtitle={ui.t('import_subtitle')}
    backLabel={ui.t('common_back')}
    {onback}
  />

  <div class="cards">
    <Card title={ui.t('import_how_title')} {play} index={0}>
      <ol>
        {#each steps as step (step)}<li>{ui.t(step)}</li>{/each}
      </ol>
    </Card>

    <label class="picker" class:disabled={state.status === 'running'}>
      <input
        type="file"
        accept=".zip,.json,application/zip,application/json"
        multiple
        disabled={state.status === 'running'}
        onchange={picked}
      />
      <span>{ui.t('import_choose')}</span>
    </label>
    <p class="muted">{ui.t('import_privacy')}</p>

    {#if state.status === 'running'}
      <div class="progress" role="status" aria-live="polite">
        <span class="bar" aria-hidden="true"></span>
        {ui.t('import_running', { count: number(state.progress?.read ?? 0) })}
      </div>
    {:else if state.status === 'done' && state.progress}
      <Card title={ui.t('import_done_title')}>
        <p role="status">
          {ui.t('import_done_summary', {
            files: number(state.progress.files),
            read: number(state.progress.read),
            inserted: number(state.progress.inserted),
          })}
        </p>
        {#if state.progress.earliest !== null && state.progress.latest !== null}
          <p class="muted">
            {ui.t('import_done_range', { from: date(state.progress.earliest), to: date(state.progress.latest) })}
          </p>
        {/if}
        {#if state.progress.skipped > 0}
          <p class="muted">{ui.t('import_done_skipped', { count: number(state.progress.skipped) })}</p>
        {/if}
      </Card>
    {:else if state.status === 'error' && state.error}
      <Notice tone="error">
        {state.error.kind === 'invalidData'
          ? ui.t('import_error', { detail: state.error.detail })
          : ui.t('import_error_generic')}
      </Notice>
    {/if}
  </div>
</main>

<style>
  .cards {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }
  ol {
    margin: 0;
    padding-left: var(--space-5);
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .picker {
    position: relative;
    display: grid;
    place-items: center;
    min-height: 64px;
    border: 2px dashed var(--outline);
    border-radius: var(--radius-m);
    background: var(--surface);
    color: var(--primary);
    font-family: var(--font-display);
    font-weight: 600;
    cursor: pointer;
  }
  .picker:has(:focus-visible) {
    outline: 3px solid var(--primary);
    outline-offset: 2px;
  }
  .picker.disabled {
    opacity: 0.5;
    cursor: progress;
  }
  input {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: inherit;
  }
  .muted {
    margin: 0;
    color: var(--on-surface-variant);
    font-size: 0.9rem;
  }
  p {
    margin: 0 0 var(--space-2);
  }
  .progress {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .bar {
    height: 6px;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--surface-high) 0%, var(--primary) 50%, var(--surface-high) 100%);
    background-size: 200% 100%;
    animation: slide 1.4s linear infinite;
  }
  @keyframes slide {
    to {
      background-position: -200% 0;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .bar {
      animation: none;
      background: var(--primary);
    }
  }
  :global(:root[data-reduce-motion='true']) .bar {
    animation: none;
    background: var(--primary);
  }
</style>
