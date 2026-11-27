<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import type { LeaderboardEntry } from '$lib/model/leaderboard'
  import Artwork from '$lib/ui/components/Artwork.svelte'
  import { useUi } from '$lib/ui/context'
  import { formatNumber } from '$lib/ui/format'
  import type { SelectedFriend } from './LeaderboardViewModel.svelte'

  interface Props {
    friend: SelectedFriend | null
    /** The friend's row in the ranking (avatar, rank and the value of the current metric). */
    entry: LeaderboardEntry | null
    name: string
    value: string
    onclose: () => void
  }

  let { friend, entry, name, value, onclose }: Props = $props()
  const ui = useUi()
  const number = (n: number) => formatNumber(n, ui.locale)
  const TOP = 5
  let dialog: HTMLDialogElement | undefined = $state()

  // The dialog mirrors the state: opening it is modal (focus trapped, Escape closes it, the page is inert).
  // Without the native API (older engines, jsdom) it falls back to the `open` attribute.
  $effect(() => {
    if (!dialog) return
    if (friend && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal()
      else dialog.setAttribute('open', '')
      // Focus the dialog itself (VoiceOver reads its name) rather than ringing the close button.
      dialog.focus()
    } else if (!friend && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close()
      else dialog.removeAttribute('open')
    }
  })

  /** Closing always goes through the state, which the effect above mirrors. */
  function dismiss() {
    onclose()
  }

  function onbackdrop(event: MouseEvent) {
    if (event.target === dialog) dismiss()
  }
</script>

<dialog
  bind:this={dialog}
  class:animate={!ui.reduceMotion}
  tabindex="-1"
  aria-labelledby="friend-dialog-title"
  aria-busy={friend?.loading ?? false}
  onclose={dismiss}
  onclick={onbackdrop}
>
  {#if friend}
    <div class="sheet">
      <header>
        <Artwork imageUrl={entry?.avatarUrl ?? null} title={name} size={64} round />
        <div class="who">
          <h2 id="friend-dialog-title">{name}</h2>
          {#if entry}<p class="muted">#{entry.rank} · {value}</p>{/if}
        </div>
        <button type="button" class="close" aria-label={ui.t('ranking_close')} onclick={dismiss}>
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </header>

      {#if friend.loading}
        <div class="placeholder" aria-hidden="true">
          {#each { length: 4 } as _, i (i)}<span class="bone"></span>{/each}
        </div>
      {:else if friend.stats}
        {@const stats = friend.stats}
        {@const tiles = [
          { label: ui.t('ranking_metric_minutes'), value: number(stats.minutes) },
          { label: ui.t('ranking_metric_streams'), value: number(stats.streams) },
          { label: ui.t('ranking_metric_artists'), value: number(stats.uniqueArtists) },
          {
            label: ui.t('ranking_metric_streak'),
            value: `${number(stats.currentStreak)} ${ui.tp('home_days_unit', stats.currentStreak)}`,
          },
        ]}
        <dl class="stats">
          {#each tiles as tile (tile.label)}
            <div>
              <dt>{tile.label}</dt>
              <dd>{tile.value}</dd>
            </div>
          {/each}
        </dl>

        {#if stats.topArtists.length > 0}
          <h3>{ui.t('ranking_top_artists')}</h3>
          <ol class="artists">
            {#each stats.topArtists.slice(0, TOP) as artist, i (i)}
              <li>
                <Artwork imageUrl={artist.imageUrl} title={artist.name} size={60} round />
                <span class="artist-name">{artist.name}</span>
              </li>
            {/each}
          </ol>
        {/if}

        {#if stats.topTracks.length > 0}
          <h3>{ui.t('ranking_top_tracks')}</h3>
          <ol class="tracks">
            {#each stats.topTracks.slice(0, TOP) as track, i (i)}
              <li>
                <span class="position">{i + 1}</span>
                <Artwork imageUrl={track.imageUrl} title={track.name} size={44} />
                <span class="track-name">{track.name}</span>
              </li>
            {/each}
          </ol>
        {/if}

        {#if stats.topArtists.length === 0 && stats.topTracks.length === 0}
          <p class="muted">{ui.t('ranking_friend_no_top')}</p>
        {/if}
      {:else}
        <p class="muted" role="alert">{ui.t('ranking_friend_error')}</p>
      {/if}
    </div>
  {/if}
</dialog>

<style>
  dialog {
    width: min(480px, calc(100% - 2 * var(--space-4)));
    max-height: min(88dvh, 760px);
    padding: 0;
    border: 1px solid var(--outline);
    border-radius: var(--radius-l);
    background: var(--surface);
    color: var(--on-surface);
    overflow: auto;
    overscroll-behavior: contain;
  }
  dialog::backdrop {
    background: rgb(0 0 0 / 0.55);
  }
  dialog.animate[open] {
    animation: rise 260ms cubic-bezier(0.2, 0, 0, 1);
  }
  dialog.animate[open]::backdrop {
    animation: fade 200ms ease-out;
  }
  @keyframes rise {
    from {
      opacity: 0;
      transform: translateY(16px) scale(0.97);
    }
  }
  @keyframes fade {
    from {
      opacity: 0;
    }
  }
  .sheet {
    padding: var(--space-4) var(--space-4) var(--space-5);
  }
  header {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin-bottom: var(--space-4);
  }
  .who {
    flex: 1;
    min-width: 0;
  }
  h2 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 1.35rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .who p {
    margin: var(--space-1) 0 0;
    font-variant-numeric: tabular-nums;
  }
  .muted {
    color: var(--on-surface-variant);
  }
  .close {
    flex: none;
    width: 48px;
    height: 48px;
    display: grid;
    place-items: center;
    border: 0;
    border-radius: 50%;
    background: var(--surface-high);
    color: var(--on-surface);
    cursor: pointer;
  }
  dialog:focus-visible {
    outline: none;
  }
  .stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    margin: 0;
    padding: var(--space-3) var(--space-2);
    border-radius: var(--radius-m);
    background: var(--surface-high);
  }
  .stats div {
    display: flex;
    flex-direction: column-reverse;
    align-items: center;
    gap: 2px;
    min-width: 0;
    text-align: center;
  }
  .stats div + div {
    border-left: 1px solid var(--outline);
  }
  dt {
    font-size: 0.75rem;
    color: var(--on-surface-variant);
  }
  dd {
    margin: 0;
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 1.15rem;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }
  h3 {
    font-size: 1rem;
    margin: var(--space-4) 0 var(--space-3);
  }
  ol {
    list-style: none;
    margin: 0;
    padding: 0;
  }
  .artists {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: var(--space-2);
  }
  .artists li {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-2);
    min-width: 0;
  }
  .artist-name {
    width: 100%;
    font-size: 0.8rem;
    text-align: center;
    line-height: 1.25;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    overflow-wrap: anywhere;
  }
  .tracks {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .tracks li {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    min-width: 0;
  }
  .position {
    width: 2ch;
    text-align: right;
    font-family: var(--font-display);
    font-weight: 700;
    color: var(--on-surface-variant);
    font-variant-numeric: tabular-nums;
  }
  .track-name {
    flex: 1;
    min-width: 0;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .placeholder {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .bone {
    height: 56px;
    border-radius: var(--radius-m);
    background: var(--surface-high);
  }
</style>
