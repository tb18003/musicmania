<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  interface Props {
    size?: number
  }

  let { size = 72 }: Props = $props()
</script>

<!-- The launcher icon's equaliser bars; decorative, the app name is always next to it. -->
<svg viewBox="30 30 50 50" width={size} height={size} aria-hidden="true">
  <g fill="none" stroke-width="8" stroke-linecap="round">
    <path d="M38 48V62" stroke="var(--violet)" />
    <path d="M49.3 38V72" stroke="var(--aqua)" />
    <path d="M60.6 43V67" stroke="var(--volt)" />
    <path d="M71.9 34V76" stroke="var(--volt)" />
  </g>
</svg>
