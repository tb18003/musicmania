<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  interface Props {
    label: string
    /** 0..1 */
    rate: number
    /** Formatted percentage ("23 %"). */
    percent: string
    description: string
  }

  let { label, rate, percent, description }: Props = $props()
  const RADIUS = 30
  const CIRCUMFERENCE = 2 * Math.PI * RADIUS
</script>

<figure role="img" aria-label={description}>
  <svg viewBox="0 0 80 80" aria-hidden="true">
    <circle class="track" cx="40" cy="40" r={RADIUS} />
    <circle
      class="value"
      cx="40"
      cy="40"
      r={RADIUS}
      stroke-dasharray={CIRCUMFERENCE}
      stroke-dashoffset={CIRCUMFERENCE * (1 - Math.min(1, Math.max(0, rate)))}
      transform="rotate(-90 40 40)"
    />
    <text x="40" y="45" text-anchor="middle">{percent}</text>
  </svg>
  <figcaption aria-hidden="true">{label}</figcaption>
</figure>

<style>
  figure {
    margin: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-1);
  }
  svg {
    width: 88px;
    height: 88px;
  }
  circle {
    fill: none;
    stroke-width: 8;
  }
  .track {
    stroke: var(--surface-highest);
  }
  .value {
    stroke: var(--primary);
    stroke-linecap: round;
    transition: stroke-dashoffset var(--duration-long) var(--easing-emphasized);
  }
  text {
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 15px;
    fill: var(--on-surface);
  }
  figcaption {
    font-size: 0.85rem;
    color: var(--on-surface-variant);
  }
</style>
