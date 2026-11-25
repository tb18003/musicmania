<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { addDays, localDateOf, startOfDay } from '$lib/domain/dates'
  import Artwork from '$lib/ui/components/Artwork.svelte'
  import Button from '$lib/ui/components/Button.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import { useUi } from '$lib/ui/context'
  import { formatDay, formatTime } from '$lib/ui/format'
  import { detailHref } from '$lib/ui/links'
  import type { HistoryState } from './HistoryViewModel.svelte'

  interface Props {
    state: HistoryState
    now: number
    onloadmore: () => void
  }

  let { state, now, onloadmore }: Props = $props()
  const ui = useUi()

  const today = $derived(localDateOf(now))
  const dayLabel = (date: string) =>
    date === today
      ? ui.t('history_today')
      : date === addDays(today, -1)
        ? ui.t('history_yesterday')
        : formatDay(startOfDay(date), ui.locale)
</script>

<main>
  <ScreenHeader title={ui.t('history_title')} />

  {#if !state.loading && state.days.length === 0}
    <p class="muted">{ui.t('history_empty')}</p>
  {/if}

  {#each state.days as day (day.date)}
    <section aria-labelledby="day-{day.date}">
      <h2 id="day-{day.date}">{dayLabel(day.date)}</h2>
      <ul>
        {#each day.plays as play (play.playedAt + play.trackName)}
          <li>
            <a
              class="row"
              href={detailHref({
                kind: 'track',
                name: play.trackName,
                artist: play.artistName,
                id: play.trackId,
                imageUrl: play.imageUrl,
              })}
            >
              <Artwork imageUrl={play.imageUrl} title={play.trackName} size={44} />
              <div class="text">
                <strong>{play.trackName}</strong>
                <span>{play.artistName}</span>
              </div>
              <time datetime={new Date(play.playedAt).toISOString()}>{formatTime(play.playedAt, ui.locale)}</time>
            </a>
          </li>
        {/each}
      </ul>
    </section>
  {/each}

  {#if !state.end && state.days.length > 0}
    <div class="more">
      <Button variant="tonal" disabled={state.loading} onclick={onloadmore}>{ui.t('history_load_more')}</Button>
    </div>
  {/if}
</main>

<style>
  section {
    margin-bottom: var(--space-5);
  }
  h2 {
    font-size: 1rem;
    color: var(--on-surface-variant);
    margin-bottom: var(--space-2);
    text-transform: capitalize;
  }
  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .row {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    min-height: var(--touch);
    color: inherit;
    text-decoration: none;
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
  .text span,
  time,
  .muted {
    color: var(--on-surface-variant);
    font-size: 0.88rem;
  }
  time {
    font-variant-numeric: tabular-nums;
  }
  .more {
    display: flex;
    justify-content: center;
  }
</style>
