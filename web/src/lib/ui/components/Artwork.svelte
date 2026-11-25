<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  interface Props {
    imageUrl: string | null
    /** Used for the initials fallback; the image itself is decorative (text sits next to it). */
    title: string
    size?: number
    round?: boolean
  }

  let { imageUrl, title, size = 48, round = false }: Props = $props()
  let failed = $state(false)

  // Same rule as Artwork.kt: first letters of the first two words, or a note.
  const initials = $derived(
    title
      .split(/[\s\-_]+/)
      .map((word) => [...word].find((char) => /[\p{L}\p{N}]/u.test(char)))
      .filter(Boolean)
      .slice(0, 2)
      .join('')
      .toUpperCase() || '♪',
  )
</script>

<div class="artwork" class:round style:width="{size}px" style:height="{size}px" aria-hidden="true">
  {#if imageUrl && !failed}
    <img
      src={imageUrl}
      alt=""
      width={size}
      height={size}
      loading="lazy"
      decoding="async"
      referrerpolicy="no-referrer"
      onerror={() => (failed = true)}
    />
  {:else}
    <span style:font-size="{Math.round(size * 0.36)}px">{initials}</span>
  {/if}
</div>

<style>
  .artwork {
    flex: none;
    display: grid;
    place-items: center;
    overflow: hidden;
    border-radius: var(--radius-s);
    background: var(--brand-gradient);
  }
  .round {
    border-radius: 50%;
  }
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  span {
    font-family: var(--font-display);
    font-weight: 700;
    color: rgb(255 255 255 / 0.92);
  }
</style>
