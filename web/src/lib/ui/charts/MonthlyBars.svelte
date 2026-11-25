<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { entrance } from '../motion'

  interface Props {
    series: readonly { month: string; streams: number }[]
    /** Short label per month (e.g. "Jan"), same length as series. */
    labels: readonly string[]
    description: string
    play?: boolean
  }

  let { series, labels, description, play = false }: Props = $props()

  const HEIGHT = 120
  const BAR = 10
  const GAP = 4
  const max = $derived(Math.max(1, ...series.map((m) => m.streams)))
  const width = $derived(Math.max(1, series.length) * (BAR + GAP))
  /** Label every month when few, otherwise one in three, always the last. */
  const showLabel = (index: number) => series.length <= 8 || index % 3 === 0 || index === series.length - 1
</script>

<svg viewBox="0 0 {width} {HEIGHT + 16}" role="img" aria-label={description} use:entrance={{ play }}>
  {#each series as month, index (month.month)}
    {@const h = month.streams === 0 ? 2 : Math.max(4, (month.streams / max) * HEIGHT)}
    <rect x={index * (BAR + GAP)} y={HEIGHT - h} width={BAR} height={h} rx="3" class:empty={month.streams === 0} />
    {#if showLabel(index)}
      <text x={index * (BAR + GAP) + BAR / 2} y={HEIGHT + 12} text-anchor="middle">{labels[index]}</text>
    {/if}
  {/each}
</svg>

<style>
  svg {
    display: block;
    width: 100%;
    max-height: 180px;
  }
  rect {
    fill: var(--primary);
  }
  rect.empty {
    fill: var(--surface-highest);
  }
  text {
    font-size: 7px;
    fill: var(--on-surface-variant);
  }
</style>
