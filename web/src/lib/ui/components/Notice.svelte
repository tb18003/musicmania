<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import type { Snippet } from 'svelte'

  interface Props {
    tone?: 'info' | 'warning' | 'error'
    children: Snippet
  }

  let { tone = 'info', children }: Props = $props()
</script>

<!-- Errors interrupt (alert); the rest is announced politely (status). -->
<div class="notice {tone}" role={tone === 'error' ? 'alert' : 'status'}>
  {@render children()}
</div>

<style>
  .notice {
    padding: var(--space-3) var(--space-4);
    border-radius: var(--radius-s);
    border-left: 4px solid var(--secondary);
    background: var(--surface-high);
    color: var(--on-surface);
    font-size: 0.92rem;
  }
  .warning {
    border-left-color: var(--tertiary);
  }
  .error {
    border-left-color: var(--negative);
  }
</style>
