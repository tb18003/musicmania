<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { entrance } from '../motion'

  interface Props {
    label: string
    value: string
    unit?: string
    detail?: string
    play?: boolean
    index?: number
  }

  let { label, value, unit, detail, play = false, index = 0 }: Props = $props()
</script>

<div class="tile" use:entrance={{ play, index }}>
  <span class="label">{label}</span>
  <span class="value">
    {value}{#if unit}<small> {unit}</small>{/if}
  </span>
  {#if detail}<span class="detail">{detail}</span>{/if}
</div>

<style>
  .tile {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    padding: var(--space-4);
    border-radius: var(--radius-m);
    background: var(--surface);
    border: 1px solid var(--outline);
    min-width: 0;
  }
  .label,
  .detail {
    color: var(--on-surface-variant);
    font-size: 0.85rem;
  }
  .value {
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 2rem;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }
  small {
    font-size: 0.9rem;
    font-weight: 600;
    color: var(--on-surface-variant);
  }
</style>
