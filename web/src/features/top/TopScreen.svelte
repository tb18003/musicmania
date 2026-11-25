<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import type { RankMovement, TimeRange, TopKind } from '$lib/model/top'
  import Artwork from '$lib/ui/components/Artwork.svelte'
  import Button from '$lib/ui/components/Button.svelte'
  import Notice from '$lib/ui/components/Notice.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import SegmentedControl from '$lib/ui/components/SegmentedControl.svelte'
  import { useUi } from '$lib/ui/context'
  import { errorMessageKey, formatNumber, formatRelative } from '$lib/ui/format'
  import type { TopState } from './TopViewModel.svelte'

  interface Props {
    state: TopState
    now: number
    onkind: (kind: TopKind) => void
    onrange: (range: TimeRange) => void
    onrefresh: () => void
    onimport: () => void
  }

  let { state, now, onkind, onrange, onrefresh, onimport }: Props = $props()
  const ui = useUi()

  const kinds = $derived<{ value: TopKind; label: string }[]>([
    { value: 'tracks', label: ui.t('top_kind_tracks') },
    { value: 'artists', label: ui.t('top_kind_artists') },
    { value: 'albums', label: ui.t('top_kind_albums') },
  ])
  const ranges = $derived<{ value: TimeRange; label: string }[]>([
    { value: 'FOUR_WEEKS', label: ui.t('top_range_four_weeks') },
    { value: 'SIX_MONTHS', label: ui.t('top_range_six_months') },
    { value: 'ONE_YEAR', label: ui.t('top_range_one_year') },
    { value: 'LIFETIME', label: ui.t('top_range_lifetime') },
  ])

  /** Visible badge and the sentence a screen reader says instead. */
  function movement(m: RankMovement): { text: string; label: string; tone: string } | null {
    switch (m.kind) {
      case 'new':
        return { text: ui.t('top_new'), label: ui.t('top_new'), tone: 'new' }
      case 'up':
        return { text: `▲${m.places}`, label: ui.tp('top_up', m.places), tone: 'up' }
      case 'down':
        return { text: `▼${m.places}`, label: ui.tp('top_down', m.places), tone: 'down' }
      case 'same':
        return { text: '–', label: ui.t('top_same'), tone: 'same' }
      default:
        return null
    }
  }
</script>

<main>
  <ScreenHeader title={ui.t('top_title')} />
  <SegmentedControl label={ui.t('top_kind')} options={kinds} value={state.kind} onchange={onkind} />
  <SegmentedControl label={ui.t('top_range')} options={ranges} value={state.range} onchange={onrange} />

  <div class="meta">
    <span>
      {#if state.range === 'LIFETIME'}
        {ui.t('top_from_history')}
      {:else if state.fetchedAt !== null}
        {ui.t('top_updated', { time: formatRelative(state.fetchedAt, now, ui.locale) })}
      {/if}
    </span>
    {#if state.range !== 'LIFETIME'}
      <Button variant="text" disabled={state.loading} onclick={onrefresh}>{ui.t('top_refresh')}</Button>
    {/if}
  </div>

  {#if state.error}
    <Notice tone="error">{ui.t(errorMessageKey(state.error))}</Notice>
  {/if}

  {#if !state.loading && state.rows.length === 0}
    {#if state.range === 'LIFETIME'}
      <p class="muted">{ui.t('top_lifetime_empty')}</p>
      <Button variant="tonal" onclick={onimport}>{ui.t('home_import_action')}</Button>
    {:else}
      <p class="muted">{ui.t('top_empty')}</p>
    {/if}
  {/if}

  <ol aria-busy={state.loading}>
    {#each state.rows as row (row.key)}
      {@const badge = movement(row.ranked.movement)}
      <li>
        <span class="rank" aria-label={ui.t('top_rank', { rank: row.ranked.rank })}>{row.ranked.rank}</span>
        <Artwork imageUrl={row.imageUrl} title={row.title} size={48} round={state.kind === 'artists'} />
        <div class="text">
          <strong>{row.title}</strong>
          {#if row.subtitle}<span>{row.subtitle}</span>{/if}
          {#if row.ranked.streams !== null}
            <span>
              {ui.tp('home_streams', row.ranked.streams, { count: formatNumber(row.ranked.streams, ui.locale) })}
            </span>
          {/if}
        </div>
        {#if badge}
          <span class="badge {badge.tone}" role="img" aria-label={badge.label}>{badge.text}</span>
        {/if}
      </li>
    {/each}
  </ol>
</main>

<style>
  .meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-2);
    min-height: var(--touch);
    color: var(--on-surface-variant);
    font-size: 0.88rem;
  }
  .muted {
    color: var(--on-surface-variant);
  }
  ol {
    list-style: none;
    margin: var(--space-2) 0 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  ol[aria-busy='true'] {
    opacity: 0.6;
  }
  li {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    min-height: 56px;
  }
  .rank {
    width: 2ch;
    text-align: right;
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 1.1rem;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
  }
  .text {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .text strong,
  .text span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .text span {
    color: var(--on-surface-variant);
    font-size: 0.88rem;
  }
  .badge {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 2px var(--space-2);
    border-radius: 999px;
    background: var(--surface-high);
  }
  .up {
    color: var(--positive);
  }
  .down {
    color: var(--negative);
  }
  .new {
    color: var(--on-primary);
    background: var(--primary);
  }
  .same {
    color: var(--on-surface-variant);
  }
</style>
