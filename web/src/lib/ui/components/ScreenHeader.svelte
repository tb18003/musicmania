<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  interface Props {
    title: string
    subtitle?: string
    /** Shows a back button with this accessible label. */
    backLabel?: string
    onback?: () => void
  }

  let { title, subtitle, backLabel, onback }: Props = $props()
</script>

<header>
  {#if onback && backLabel}
    <button type="button" class="back" aria-label={backLabel} onclick={onback}>
      <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
        <path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
      </svg>
    </button>
  {/if}
  <div>
    <h1>{title}</h1>
    {#if subtitle}<p>{subtitle}</p>{/if}
  </div>
</header>

<style>
  header {
    display: flex;
    align-items: flex-start;
    gap: var(--space-2);
    margin-bottom: var(--space-5);
  }
  h1 {
    font-size: 2rem;
  }
  p {
    margin: var(--space-2) 0 0;
    color: var(--on-surface-variant);
  }
  .back {
    flex: none;
    width: var(--touch);
    height: var(--touch);
    margin-left: calc(-1 * var(--space-3));
    display: grid;
    place-items: center;
    border: 0;
    border-radius: 50%;
    background: transparent;
    color: var(--on-background);
    cursor: pointer;
  }
</style>
