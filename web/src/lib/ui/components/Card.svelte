<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import type { Snippet } from 'svelte'
  import { entrance } from '../motion'

  interface Props {
    title?: string
    play?: boolean
    index?: number
    children: Snippet
  }

  let { title, play = false, index = 0, children }: Props = $props()
</script>

<section class="card" use:entrance={{ play, index }}>
  {#if title}<h2>{title}</h2>{/if}
  {@render children()}
</section>

<style>
  .card {
    background: var(--surface);
    border: 1px solid var(--outline);
    border-radius: var(--radius-m);
    padding: var(--space-5);
  }
  h2 {
    font-size: 1.1rem;
    margin-bottom: var(--space-3);
  }
</style>
