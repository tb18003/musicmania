<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { onDestroy } from 'svelte'
  import { isEmptyRecap, RECAP_PERIODS, type RecapPeriod } from '$lib/domain/recap'
  import Artwork from '$lib/ui/components/Artwork.svelte'
  import Button from '$lib/ui/components/Button.svelte'
  import { useUi } from '$lib/ui/context'
  import { formatMinutes, formatNumber } from '$lib/ui/format'
  import type { MessageKey } from '$lib/ui/i18n/en'
  import { createShareImage, shareOrDownload, type ShareCardData } from '$lib/ui/share-card'
  import type { RecapState } from './RecapViewModel.svelte'

  interface Props {
    state: RecapState
    onperiod: (period: RecapPeriod) => void
    onclose: () => void
    onimport: () => void
  }

  // Renamed locally: a binding called `state` would turn the `$state` rune into a store read.
  let { state: view, onperiod, onclose, onimport }: Props = $props()
  const ui = useUi()

  /** Same pacing as the app's stories (RecapScreen.PAGE_DURATION_MS). */
  const PAGE_MS = 5_500
  const TICK_MS = 50
  const HOLD_MS = 250

  let page = $state(0)
  let progress = $state(0)
  let paused = $state(false)
  let sharing = $state(false)
  let shareFailed = $state(false)

  const pages = $derived(view.pages)
  const recap = $derived(view.recap)
  const current = $derived(pages[page] ?? 'INTRO')
  // With reduced motion, stories never move on their own: the user turns the pages.
  const autoplay = $derived(!ui.reduceMotion && !paused && page < pages.length - 1)
  const periodLabel = $derived(ui.t(`recap_period_${view.period}` as MessageKey))
  const number = (value: number) => formatNumber(value, ui.locale)

  // A new period or recap starts from the first story.
  $effect(() => {
    void view.recap
    page = 0
    progress = 0
  })

  const timer = setInterval(() => {
    if (!autoplay || !recap || isEmptyRecap(recap)) return
    progress += TICK_MS / PAGE_MS
    if (progress >= 1) next()
  }, TICK_MS)
  onDestroy(() => clearInterval(timer))

  function next() {
    if (page < pages.length - 1) page++
    progress = 0
  }
  function previous() {
    if (page > 0) page--
    progress = 0
  }

  // Tap to move, press and hold to pause (like Instagram stories and the app).
  let pressedAt = 0
  function press() {
    pressedAt = performance.now()
    paused = true
  }
  function release(move: () => void) {
    const held = performance.now() - pressedAt > HOLD_MS
    paused = false
    if (!held) move()
  }

  function keydown(event: KeyboardEvent) {
    if (event.key === 'ArrowRight') next()
    else if (event.key === 'ArrowLeft') previous()
    else if (event.key === 'Escape') onclose()
    else if (event.key === ' ') {
      paused = !paused
      event.preventDefault()
    }
  }

  async function share() {
    if (!recap) return
    sharing = true
    shareFailed = false
    const data: ShareCardData = {
      brand: ui.t('app_name'),
      period: periodLabel,
      minutes: formatMinutes(recap.summary.listenedMs, ui.locale),
      minutesLabel: ui.t('recap_share_card_minutes'),
      streamsLine: ui.t('recap_streams', {
        streams: number(recap.summary.streams),
        tracks: number(recap.summary.uniqueTracks),
      }),
      topArtistsTitle: ui.t('recap_share_card_top_artists'),
      topArtists: recap.topArtists.map((r) => ({ name: r.item.name, imageUrl: r.item.imageUrl })),
      topTracksTitle: ui.t('recap_share_card_top_tracks'),
      topTracks: recap.topTracks.map((r) => `${r.item.name} · ${r.item.artistName}`),
      footer: ui.t('recap_share_card_footer'),
    }
    try {
      const image = await createShareImage(data)
      await shareOrDownload(image, `musicmania-recap-${view.period.toLowerCase()}.png`)
    } catch {
      shareFailed = true
    } finally {
      sharing = false
    }
  }
</script>

<svelte:window onkeydown={keydown} />

<div class="recap" role="dialog" aria-modal="true" aria-label={ui.t('recap_intro_kicker')} data-page={page % 4}>
  <div class="top">
    <div
      class="segments"
      role="progressbar"
      aria-label={ui.t('recap_progress', { current: page + 1, total: Math.max(1, pages.length) })}
    >
      {#each pages as story, index (story)}
        <span class="segment">
          <span style:width="{index < page ? 100 : index === page ? progress * 100 : 0}%"></span>
        </span>
      {/each}
    </div>
    <div class="bar">
      <select
        aria-label={ui.t('recap_period')}
        value={view.period}
        onchange={(e) => onperiod(e.currentTarget.value as RecapPeriod)}
      >
        {#each RECAP_PERIODS as period (period)}
          <option value={period}>{ui.t(`recap_period_${period}` as MessageKey)}</option>
        {/each}
      </select>
      <button
        type="button"
        class="icon"
        aria-label={paused ? ui.t('recap_resume') : ui.t('recap_pause')}
        onclick={() => (paused = !paused)}
      >
        {paused ? '▶' : '❚❚'}
      </button>
      <button type="button" class="icon" aria-label={ui.t('recap_close')} onclick={onclose}>✕</button>
    </div>
  </div>

  {#if recap && isEmptyRecap(recap)}
    <div class="story">
      <h2>{ui.t('recap_empty_title')}</h2>
      <p>{ui.t('recap_empty_message')}</p>
      <Button onclick={onimport}>{ui.t('settings_import')}</Button>
    </div>
  {:else if recap}
    <!-- Tap zones: left goes back, right goes forward; holding pauses. -->
    <button
      type="button"
      class="zone back"
      aria-label={ui.t('recap_previous')}
      onpointerdown={press}
      onpointerup={() => release(previous)}
      onpointercancel={() => (paused = false)}
    ></button>
    <button
      type="button"
      class="zone forward"
      aria-label={ui.t('recap_next')}
      onpointerdown={press}
      onpointerup={() => release(next)}
      onpointercancel={() => (paused = false)}
    ></button>

    <div class="story" aria-live="polite">
      {#if current === 'INTRO'}
        <p class="kicker">{ui.t('recap_intro_kicker')}</p>
        <h2 class="huge">{periodLabel}</h2>
        <p>{ui.t('recap_intro_body')}</p>
      {:else if current === 'MINUTES'}
        <p class="kicker">{ui.t('recap_minutes_kicker')}</p>
        <h2 class="huge">{formatMinutes(recap.summary.listenedMs, ui.locale)}</h2>
        <p>{ui.t('recap_minutes_unit')}</p>
        {#if recap.summary.listenedMs >= 86_400_000}
          <p>
            {ui.t('recap_minutes_days', {
              days: new Intl.NumberFormat(ui.locale, { maximumFractionDigits: 1 }).format(
                recap.summary.listenedMs / 86_400_000,
              ),
            })}
          </p>
        {/if}
        <p>
          {ui.t('recap_streams', {
            streams: number(recap.summary.streams),
            tracks: number(recap.summary.uniqueTracks),
          })}
        </p>
      {:else if current === 'TOP_ARTIST' && recap.topArtists[0]}
        {@const artist = recap.topArtists[0]}
        <p class="kicker">{ui.t('recap_top_artist_kicker')}</p>
        <Artwork imageUrl={artist.item.imageUrl} title={artist.item.name} size={180} round />
        <h2 class="big">{artist.item.name}</h2>
        <p>
          {ui.t('recap_top_artist_body', {
            streams: number(artist.streams ?? 0),
            minutes: formatMinutes(artist.listenedMs ?? 0, ui.locale),
          })}
        </p>
      {:else if current === 'TOP_TRACKS'}
        <p class="kicker">{ui.t('recap_top_tracks_kicker')}</p>
        <ol class="tracks">
          {#each recap.topTracks as track (track.rank)}
            <li>
              <span class="rank">{track.rank}</span>
              <Artwork imageUrl={track.item.imageUrl} title={track.item.name} size={56} />
              <span class="text"><strong>{track.item.name}</strong><span>{track.item.artistName}</span></span>
            </li>
          {/each}
        </ol>
      {:else if current === 'PERSONALITY' && recap.personality}
        <p class="kicker">{ui.t('recap_personality_kicker')}</p>
        <h2 class="big">{ui.t(`insights_personality_${recap.personality}` as MessageKey)}</h2>
        <p>{ui.t(`insights_personality_${recap.personality}_body` as MessageKey)}</p>
      {:else if current === 'DISCOVERIES'}
        <p class="kicker">{ui.t('recap_discoveries_kicker')}</p>
        <h2 class="huge">{number(recap.newArtists)}</h2>
        <p>{ui.t('recap_new_artists')}</p>
        <h2 class="huge">{number(recap.longestStreak)}</h2>
        <p>{ui.t('recap_streak')}</p>
      {:else if current === 'SHARE'}
        <p class="kicker">{ui.t('recap_share_kicker')}</p>
        <h2 class="big">{periodLabel}</h2>
        <div class="share">
          <Button disabled={sharing} onclick={share}>{sharing ? ui.t('recap_sharing') : ui.t('recap_share')}</Button>
        </div>
        {#if shareFailed}<p role="alert">{ui.t('recap_share_failed')}</p>{/if}
      {/if}
    </div>
  {/if}
</div>

<style>
  .recap {
    position: fixed;
    inset: 0;
    z-index: 30;
    display: flex;
    flex-direction: column;
    padding: calc(env(safe-area-inset-top) + var(--space-3)) var(--space-4)
      calc(env(safe-area-inset-bottom) + var(--space-4));
    color: #fff;
    background: linear-gradient(160deg, #5b4bff, #0a0a0f 70%);
    transition: background var(--duration-long);
    overflow: hidden;
  }
  .recap[data-page='1'] {
    background: linear-gradient(160deg, #4c7a00, #0a0a0f 70%);
  }
  .recap[data-page='2'] {
    background: linear-gradient(160deg, #d9483a, #0a0a0f 70%);
  }
  .recap[data-page='3'] {
    background: linear-gradient(160deg, #0f7d8f, #0a0a0f 70%);
  }
  .top {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .segments {
    display: flex;
    gap: 4px;
  }
  .segment {
    flex: 1;
    height: 4px;
    border-radius: 2px;
    background: rgb(255 255 255 / 0.3);
    overflow: hidden;
  }
  .segment span {
    display: block;
    height: 100%;
    background: #fff;
  }
  .bar {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }
  select {
    flex: 1;
    min-height: var(--touch);
    padding: 0 var(--space-6) 0 var(--space-4);
    border: 0;
    border-radius: 999px;
    /* Without this, Safari draws its own white control and ignores the colours. */
    appearance: none;
    -webkit-appearance: none;
    background:
      url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8'%3E%3Cpath d='M1 1l5 5 5-5' fill='none' stroke='white' stroke-width='2'/%3E%3C/svg%3E")
        no-repeat right var(--space-4) center,
      rgb(255 255 255 / 0.15);
    color: #fff;
    font-weight: 600;
  }
  select option {
    color: initial;
  }
  .icon {
    width: var(--touch);
    height: var(--touch);
    border: 0;
    border-radius: 50%;
    background: rgb(255 255 255 / 0.15);
    color: #fff;
    font-size: 1rem;
    cursor: pointer;
  }
  .zone {
    position: absolute;
    top: 120px;
    bottom: 0;
    z-index: 1;
    border: 0;
    background: transparent;
    cursor: pointer;
    -webkit-touch-callout: none;
    user-select: none;
  }
  .zone:focus-visible {
    outline: 3px solid #fff;
    outline-offset: -6px;
  }
  .back {
    left: 0;
    width: 30%;
  }
  .forward {
    right: 0;
    width: 70%;
  }
  .story {
    position: relative;
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: var(--space-3);
    max-width: 560px;
    width: 100%;
    margin: 0 auto;
    /* Above the tap zones, but taps pass through to them; only real controls take them. */
    z-index: 2;
    pointer-events: none;
  }
  .story :global(button),
  .story :global(a) {
    pointer-events: auto;
  }
  .kicker {
    margin: 0;
    font-family: var(--font-display);
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    opacity: 0.85;
  }
  h2 {
    margin: 0;
    font-family: var(--font-display);
  }
  .huge {
    font-size: clamp(3.5rem, 18vw, 6rem);
    line-height: 1;
    font-variant-numeric: tabular-nums;
  }
  .big {
    font-size: clamp(2rem, 9vw, 3rem);
  }
  p {
    margin: 0;
    font-size: 1.1rem;
    opacity: 0.92;
  }
  .tracks {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }
  .tracks li {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }
  .rank {
    width: 1.5ch;
    font-family: var(--font-display);
    font-size: 1.6rem;
    font-weight: 700;
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
    opacity: 0.8;
    font-size: 0.9rem;
  }
  .share {
    margin-top: var(--space-4);
  }
</style>
