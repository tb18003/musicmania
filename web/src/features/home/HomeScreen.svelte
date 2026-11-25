<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import Artwork from '$lib/ui/components/Artwork.svelte'
  import Button from '$lib/ui/components/Button.svelte'
  import Card from '$lib/ui/components/Card.svelte'
  import Notice from '$lib/ui/components/Notice.svelte'
  import StatTile from '$lib/ui/components/StatTile.svelte'
  import { useUi } from '$lib/ui/context'
  import { errorMessageKey, formatMinutes, formatNumber, formatRelative } from '$lib/ui/format'
  import { entrance, entrances } from '$lib/ui/motion'
  import type { HomeState } from './HomeViewModel.svelte'

  interface Props {
    state: HomeState
    now: number
    onsync: () => void
  }

  let { state, now, onsync }: Props = $props()
  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('home')
  const number = (value: number) => formatNumber(value, ui.locale)
</script>

<main>
  <header use:entrance={{ play, index: 0 }}>
    <h1>
      {state.profile?.displayName
        ? ui.t('home_greeting', { name: state.profile.displayName })
        : ui.t('home_greeting_plain')}
    </h1>
    <p class="sync" aria-live="polite">
      {#if state.sync.running}
        {ui.t('home_syncing')}
      {:else if state.sync.lastSuccess !== null}
        {ui.t('home_synced', { time: formatRelative(state.sync.lastSuccess, now, ui.locale) })}
      {:else}
        {ui.t('home_never_synced')}
      {/if}
    </p>
  </header>

  {#if state.error}
    <Notice tone="error">{ui.t(errorMessageKey(state.error))}</Notice>
  {:else if state.sync.possibleGap}
    <Notice tone="warning">{ui.t('home_gap_warning')}</Notice>
  {/if}

  {#if state.nowPlaying}
    <Card title={ui.t('home_now_playing')} {play} index={1}>
      <div class="row">
        <Artwork imageUrl={state.nowPlaying.imageUrl} title={state.nowPlaying.trackName} size={56} />
        <div class="text">
          <strong>{state.nowPlaying.trackName}</strong>
          <span>{state.nowPlaying.artistName}{state.nowPlaying.isPlaying ? '' : ` · ${ui.t('home_paused')}`}</span>
        </div>
      </div>
    </Card>
  {/if}

  {#if !state.loading && state.totalPlays === 0}
    <Card title={ui.t('home_empty_title')} {play} index={2}>
      <p class="muted">{ui.t('home_empty_message')}</p>
    </Card>
  {:else}
    <div class="grid">
      <StatTile
        label={ui.t('home_today')}
        value={formatMinutes(state.today.listenedMs, ui.locale)}
        unit={ui.t('home_minutes')}
        detail={ui.tp('home_streams', state.today.streams, { count: number(state.today.streams) })}
        {play}
        index={2}
      />
      <StatTile
        label={ui.t('home_this_week')}
        value={formatMinutes(state.week.listenedMs, ui.locale)}
        unit={ui.t('home_minutes')}
        detail={ui.tp('home_streams', state.week.streams, { count: number(state.week.streams) })}
        {play}
        index={3}
      />
      <StatTile
        label={ui.t('home_streak')}
        value={number(state.streaks.current)}
        unit={ui.tp('home_days_unit', state.streaks.current)}
        detail={ui.tp('home_streak_best', state.streaks.longest, { count: number(state.streaks.longest) })}
        {play}
        index={4}
      />
    </div>

    {#if state.topArtist}
      <Card title={ui.t('home_top_artist')} {play} index={5}>
        <div class="row">
          <Artwork imageUrl={state.topArtist.imageUrl} title={state.topArtist.artistName} size={56} round />
          <div class="text">
            <strong>{state.topArtist.artistName}</strong>
            <span>{ui.tp('home_streams', state.topArtist.streams, { count: number(state.topArtist.streams) })}</span>
          </div>
        </div>
      </Card>
    {/if}
  {/if}

  <p class="hint">{ui.t('home_open_daily')}</p>
  <div class="actions">
    <Button variant="tonal" disabled={state.sync.running} onclick={onsync}>{ui.t('home_sync_now')}</Button>
  </div>
</main>

<style>
  main {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }
  h1 {
    font-size: 2rem;
  }
  .sync,
  .muted,
  .hint {
    margin: var(--space-1) 0 0;
    color: var(--on-surface-variant);
    font-size: 0.9rem;
  }
  .muted {
    margin: 0;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-3);
  }
  .grid > :global(:last-child:nth-child(odd)) {
    grid-column: 1 / -1;
  }
  .row {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }
  .text {
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
    font-size: 0.9rem;
  }
  .actions {
    display: flex;
    justify-content: center;
  }
</style>
