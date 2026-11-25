<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import type { LeaderboardEntry, LeaderboardMetric, LeaderboardPeriod } from '$lib/model/leaderboard'
  import Artwork from '$lib/ui/components/Artwork.svelte'
  import Button from '$lib/ui/components/Button.svelte'
  import Card from '$lib/ui/components/Card.svelte'
  import Notice from '$lib/ui/components/Notice.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import SegmentedControl from '$lib/ui/components/SegmentedControl.svelte'
  import { useUi } from '$lib/ui/context'
  import { formatNumber } from '$lib/ui/format'
  import type { LeaderboardState } from './LeaderboardViewModel.svelte'

  interface Props {
    state: LeaderboardState
    onmetric: (metric: LeaderboardMetric) => void
    onperiod: (period: LeaderboardPeriod) => void
    onenablesharing: () => void
    onfriend: (spotifyUserId: string) => void
    onretry: () => void
    oncopy: (text: string) => Promise<boolean>
  }

  // Renamed locally: a binding called `state` would turn the `$state` rune into a store read.
  let { state: view, onmetric, onperiod, onenablesharing, onfriend, onretry, oncopy }: Props = $props()
  const ui = useUi()
  let copied = $state(false)
  const number = (value: number) => formatNumber(value, ui.locale)

  const metrics = $derived<{ value: LeaderboardMetric; label: string }[]>([
    { value: 'minutes', label: ui.t('ranking_metric_minutes') },
    { value: 'streams', label: ui.t('ranking_metric_streams') },
    { value: 'streak', label: ui.t('ranking_metric_streak') },
    { value: 'unique_artists', label: ui.t('ranking_metric_artists') },
  ])
  const periods = $derived<{ value: LeaderboardPeriod; label: string }[]>([
    { value: 'week', label: ui.t('ranking_period_week') },
    { value: 'month', label: ui.t('ranking_period_month') },
    { value: 'year', label: ui.t('ranking_period_year') },
    { value: 'all_time', label: ui.t('ranking_period_all_time') },
  ])

  function value(entry: LeaderboardEntry): string {
    switch (view.metric) {
      case 'minutes':
        return ui.t('ranking_minutes_value', { value: number(entry.value) })
      case 'streams':
        return ui.tp('home_streams', entry.value, { count: number(entry.value) })
      case 'streak':
        return `${number(entry.value)} ${ui.tp('home_days_unit', entry.value)}`
      case 'unique_artists':
        return ui.tp('ranking_artists_value', entry.value, { count: number(entry.value) })
    }
  }

  async function copy(id: string) {
    copied = await oncopy(id)
  }
</script>

<main>
  <ScreenHeader title={ui.t('ranking_title')} subtitle={ui.t('ranking_subtitle')} />

  {#if view.content.kind === 'notAllowlisted'}
    {@const id = view.content.spotifyUserId}
    <Card title={ui.t('ranking_not_allowlisted_title')}>
      <p class="muted">{ui.t('ranking_not_allowlisted_message')}</p>
      {#if id}
        <p class="id"><code>{id}</code></p>
        <Button variant="tonal" onclick={() => copy(id)}>
          {copied ? ui.t('ranking_copied') : ui.t('ranking_copy_id')}
        </Button>
      {/if}
    </Card>
  {:else if view.content.kind === 'sharingOff'}
    <Card title={ui.t('ranking_sharing_off_title')}>
      <p class="muted">{ui.t('ranking_sharing_off_message')}</p>
      <Button disabled={view.enablingSharing} onclick={onenablesharing}>{ui.t('ranking_enable_sharing')}</Button>
    </Card>
  {:else if view.content.kind === 'error'}
    <Notice tone="error">{ui.t('ranking_error')}</Notice>
    <div><Button variant="tonal" onclick={onretry}>{ui.t('ranking_retry')}</Button></div>
  {:else}
    <SegmentedControl label={ui.t('ranking_metric')} options={metrics} value={view.metric} onchange={onmetric} />
    <SegmentedControl label={ui.t('ranking_period')} options={periods} value={view.period} onchange={onperiod} />

    {#if view.content.kind === 'ranking' && view.content.entries.length === 0}
      <Card title={ui.t('ranking_empty_title')}>
        <p class="muted">{ui.t('ranking_empty_message')}</p>
      </Card>
    {/if}

    <ol aria-busy={view.content.kind === 'loading'}>
      {#each view.content.kind === 'ranking' ? view.content.entries : [] as entry (entry.spotifyUserId)}
        {@const name = entry.isMe ? ui.t('ranking_you') : (entry.displayName ?? entry.spotifyUserId)}
        {@const open = view.expanded?.spotifyUserId === entry.spotifyUserId}
        <li class:me={entry.isMe}>
          <button
            type="button"
            class="row"
            aria-expanded={open}
            aria-label={ui.t('ranking_details', { name })}
            onclick={() => onfriend(entry.spotifyUserId)}
          >
            <span class="rank" class:podium={entry.rank <= 3}>{entry.rank}</span>
            <Artwork imageUrl={entry.avatarUrl} title={entry.displayName ?? entry.spotifyUserId} size={44} round />
            <span class="name">{name}</span>
            <span class="value">{value(entry)}</span>
          </button>
          {#if open}
            <div class="details">
              {#if view.expanded?.stats}
                {@const stats = view.expanded.stats}
                <p>
                  {ui.t('ranking_minutes_value', { value: number(stats.minutes) })} ·
                  {ui.tp('home_streams', stats.streams, { count: number(stats.streams) })} ·
                  {ui.tp('ranking_artists_value', stats.uniqueArtists, { count: number(stats.uniqueArtists) })}
                </p>
                {#if stats.topArtists.length > 0}
                  <h3>{ui.t('ranking_top_artists')}</h3>
                  <p class="muted">{stats.topArtists.slice(0, 5).join(' · ')}</p>
                {/if}
                {#if stats.topTracks.length > 0}
                  <h3>{ui.t('ranking_top_tracks')}</h3>
                  <p class="muted">{stats.topTracks.slice(0, 5).join(' · ')}</p>
                {/if}
              {:else}
                <p class="muted" aria-busy="true">…</p>
              {/if}
            </div>
          {/if}
        </li>
      {/each}
    </ol>
  {/if}
</main>

<style>
  main {
    display: flex;
    flex-direction: column;
  }
  .muted {
    color: var(--on-surface-variant);
  }
  .id code {
    font-size: 1.05rem;
    padding: var(--space-1) var(--space-2);
    border-radius: var(--radius-s);
    background: var(--surface-high);
    user-select: all;
    -webkit-user-select: all;
  }
  ol {
    list-style: none;
    margin: var(--space-3) 0 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  ol[aria-busy='true'] {
    opacity: 0.6;
  }
  li {
    border-radius: var(--radius-m);
    background: var(--surface);
    border: 1px solid var(--outline);
  }
  li.me {
    border-color: var(--primary);
  }
  .row {
    width: 100%;
    min-height: 64px;
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-4);
    border: 0;
    background: none;
    text-align: left;
    cursor: pointer;
  }
  .rank {
    width: 2ch;
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 1.2rem;
    color: var(--on-surface-variant);
    text-align: right;
  }
  .podium {
    color: var(--primary);
  }
  .name {
    flex: 1;
    min-width: 0;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .value {
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    white-space: nowrap;
  }
  .details {
    padding: 0 var(--space-4) var(--space-4);
  }
  .details p {
    margin: 0 0 var(--space-2);
  }
  h3 {
    font-size: 0.9rem;
    margin: var(--space-2) 0 var(--space-1);
  }
</style>
