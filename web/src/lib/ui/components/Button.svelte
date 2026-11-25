<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import type { Snippet } from 'svelte'

  interface Props {
    variant?: 'primary' | 'tonal' | 'text'
    disabled?: boolean
    onclick?: () => void
    children: Snippet
  }

  let { variant = 'primary', disabled = false, onclick, children }: Props = $props()
</script>

<button type="button" class={variant} {disabled} {onclick}>
  {@render children()}
</button>

<style>
  button {
    min-height: var(--touch);
    min-width: var(--touch);
    padding: 0 var(--space-5);
    border: 0;
    border-radius: 999px;
    font-family: var(--font-display);
    font-weight: 600;
    font-size: 1rem;
    cursor: pointer;
    transition:
      transform var(--duration-short) var(--easing-emphasized),
      opacity var(--duration-short);
  }
  button:active:not(:disabled) {
    transform: scale(0.97);
  }
  button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
  .primary {
    background: var(--primary);
    color: var(--on-primary);
  }
  .tonal {
    background: var(--surface-highest);
    color: var(--on-surface);
  }
  .text {
    background: transparent;
    color: var(--primary);
    padding: 0 var(--space-3);
  }
</style>
