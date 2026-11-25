<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { entrance } from '../motion'

  interface Props {
    /** Milliseconds per hour of the day (24 values). */
    clock: readonly number[]
    /** What a screen reader says instead of the drawing. */
    description: string
    peak: number | null
    play?: boolean
  }

  let { clock, description, peak, play = false }: Props = $props()

  const SIZE = 220
  const CENTER = SIZE / 2
  const INNER = 40
  const OUTER = 100
  const max = $derived(Math.max(1, ...clock))

  /** One radial bar per hour, midnight at the top, clockwise. */
  const bars = $derived(
    clock.map((ms, hour) => {
      const angle = (hour / 24) * 2 * Math.PI - Math.PI / 2
      const length = ms > 0 ? Math.max(4, (ms / max) * (OUTER - INNER)) : 2
      const [cos, sin] = [Math.cos(angle), Math.sin(angle)]
      return {
        hour,
        x1: CENTER + cos * INNER,
        y1: CENTER + sin * INNER,
        x2: CENTER + cos * (INNER + length),
        y2: CENTER + sin * (INNER + length),
      }
    }),
  )
  const labels = [0, 6, 12, 18].map((hour) => {
    const angle = (hour / 24) * 2 * Math.PI - Math.PI / 2
    return { hour, x: CENTER + Math.cos(angle) * 22, y: CENTER + Math.sin(angle) * 22 + 4 }
  })
</script>

<svg viewBox="0 0 {SIZE} {SIZE}" role="img" aria-label={description} use:entrance={{ play }}>
  {#each bars as bar (bar.hour)}
    <line
      x1={bar.x1}
      y1={bar.y1}
      x2={bar.x2}
      y2={bar.y2}
      class:peak={bar.hour === peak}
      stroke-width="7"
      stroke-linecap="round"
    />
  {/each}
  {#each labels as label (label.hour)}
    <text x={label.x} y={label.y} text-anchor="middle">{label.hour}</text>
  {/each}
</svg>

<style>
  svg {
    display: block;
    width: 100%;
    max-width: 280px;
    margin: 0 auto;
  }
  line {
    stroke: var(--secondary);
    opacity: 0.75;
  }
  line.peak {
    stroke: var(--primary);
    opacity: 1;
  }
  text {
    font-size: 11px;
    fill: var(--on-surface-variant);
  }
</style>
