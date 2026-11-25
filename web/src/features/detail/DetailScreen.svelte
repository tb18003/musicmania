<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import type { DetailKind } from '$lib/model/detail'
  import MonthlyBars from '$lib/ui/charts/MonthlyBars.svelte'
  import Artwork from '$lib/ui/components/Artwork.svelte'
  import Card from '$lib/ui/components/Card.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import StatTile from '$lib/ui/components/StatTile.svelte'
  import { useUi } from '$lib/ui/context'
  import { formatMinutes, formatNumber } from '$lib/ui/format'
  import type { MessageKey } from '$lib/ui/i18n/en'
  import { spotifyUrl } from '$lib/ui/links'
  import type { DetailState } from './DetailViewModel.svelte'

  interface Props {
    state: DetailState
    onback: () => void
  }

  // Renamed locally: a binding called `state` would turn the `$state` rune into a store read.
  let { state: view, onback }: Props = $props()
  const ui = useUi()

  const date = (epochMs: number | null) =>
    epochMs === null
      ? ui.t('detail_never')
      : new Intl.DateTimeFormat(ui.locale, { dateStyle: 'medium' }).format(epochMs)
  const monthDate = (month: string) => {
    const [year, m] = month.split('-').map(Number) as [number, number]
    return new Date(year, m - 1, 1)
  }
  const monthShort = (month: string) => new Intl.DateTimeFormat(ui.locale, { month: 'short' }).format(monthDate(month))
  const monthLong = (month: string) =>
    new Intl.DateTimeFormat(ui.locale, { month: 'long', year: 'numeric' }).format(monthDate(month))

  const target = $derived(view.target)
  const stats = $derived(view.stats)
  const link = $derived(target ? spotifyUrl(target) : null)
  const peak = $derived(
    stats && stats.monthly.length > 0
      ? stats.monthly.reduce((best, month) => (month.streams > best.streams ? month : best))
      : null,
  )
  const kindLabel = (kind: DetailKind) => ui.t(`detail_kind_${kind}` as MessageKey)
</script>

<main>
  {#if target}
    <ScreenHeader title={kindLabel(target.kind)} backLabel={ui.t('common_back')} {onback} />

    <div class="hero">
      <Artwork imageUrl={view.imageUrl} title={target.name} size={160} round={target.kind === 'artist'} />
      <h2 class="name">{target.name}</h2>
      {#if target.artist}<p class="muted">{target.artist}</p>{/if}
      {#if view.genres.length > 0}
        <ul class="genres" aria-label={ui.t('detail_genres')}>
          {#each view.genres.slice(0, 5) as genre (genre)}<li>{genre}</li>{/each}
        </ul>
      {/if}
      {#if link}
        <a class="spotify" href={link} target="_blank" rel="noopener noreferrer">{ui.t('detail_open_spotify')}</a>
      {/if}
    </div>

    {#if stats && stats.streams === 0}
      <p class="muted center">{ui.t('detail_no_plays')}</p>
    {:else if stats}
      <div class="grid">
        <StatTile label={ui.t('detail_streams')} value={formatNumber(stats.streams, ui.locale)} />
        <StatTile label={ui.t('detail_minutes')} value={formatMinutes(stats.listenedMs, ui.locale)} />
        <StatTile label={ui.t('detail_first')} value={date(stats.firstPlayed)} />
        <StatTile label={ui.t('detail_last')} value={date(stats.lastPlayed)} />
      </div>
      {#if peak && stats.monthly.length > 1}
        {@const first = stats.monthly[0]}
        {@const last = stats.monthly[stats.monthly.length - 1]}
        <Card title={ui.t('detail_monthly')}>
          <MonthlyBars
            series={stats.monthly}
            labels={stats.monthly.map((m) => monthShort(m.month))}
            description={ui.t('detail_monthly_description', {
              from: first ? monthLong(first.month) : '',
              to: last ? monthLong(last.month) : '',
              peak: monthLong(peak.month),
              count: formatNumber(peak.streams, ui.locale),
            })}
          />
        </Card>
      {/if}
    {/if}
  {/if}
</main>

<style>
  main {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }
  .hero {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-2);
    text-align: center;
  }
  .name {
    font-size: 1.8rem;
    margin-top: var(--space-3);
  }
  .muted {
    margin: 0;
    color: var(--on-surface-variant);
  }
  .center {
    text-align: center;
  }
  .genres {
    list-style: none;
    margin: var(--space-2) 0 0;
    padding: 0;
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: var(--space-2);
  }
  .genres li {
    padding: 2px var(--space-3);
    border-radius: 999px;
    background: var(--surface-high);
    font-size: 0.85rem;
  }
  .spotify {
    display: inline-flex;
    align-items: center;
    min-height: var(--touch);
    font-weight: 600;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-3);
  }
  .grid :global(.value) {
    font-size: 1.4rem;
  }
</style>
