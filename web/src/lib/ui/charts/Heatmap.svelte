<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { entrance } from '../motion'

  interface Props {
    /** [weekday][hour] milliseconds, weekday 0 = Monday. */
    grid: readonly (readonly number[])[]
    /** Short weekday names, Monday first. */
    weekdays: readonly string[]
    description: string
    play?: boolean
  }

  let { grid, weekdays, description, play = false }: Props = $props()

  const CELL = 11
  const GAP = 2
  const LEFT = 30
  const TOP = 14
  const max = $derived(Math.max(1, ...grid.flat()))
  const width = LEFT + 24 * (CELL + GAP)
  const height = TOP + 7 * (CELL + GAP)
</script>

<svg viewBox="0 0 {width} {height}" role="img" aria-label={description} use:entrance={{ play }}>
  {#each [0, 6, 12, 18] as hour (hour)}
    <text class="hour" x={LEFT + hour * (CELL + GAP)} y={TOP - 4}>{hour}</text>
  {/each}
  {#each grid as row, day (day)}
    <text class="day" x={LEFT - 4} y={TOP + day * (CELL + GAP) + CELL - 2} text-anchor="end">{weekdays[day]}</text>
    {#each row as ms, hour (hour)}
      <rect
        x={LEFT + hour * (CELL + GAP)}
        y={TOP + day * (CELL + GAP)}
        width={CELL}
        height={CELL}
        rx="2"
        style:opacity={ms === 0 ? 1 : 0.2 + 0.8 * (ms / max)}
        class:filled={ms > 0}
      />
    {/each}
  {/each}
</svg>

<style>
  svg {
    display: block;
    width: 100%;
  }
  rect {
    fill: var(--surface-highest);
  }
  rect.filled {
    fill: var(--primary);
  }
  text {
    font-size: 8px;
    fill: var(--on-surface-variant);
  }
</style>
