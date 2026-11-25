<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts" generics="T extends string">
  interface Item {
    value: T
    label: string
    /** SVG path data, 24×24 viewBox, stroked. */
    icon: string
  }

  interface Props {
    label: string
    items: readonly Item[]
    /** null when the current screen is not a tab (e.g. Settings). */
    current: T | null
    onselect: (value: T) => void
  }

  let { label, items, current, onselect }: Props = $props()
</script>

<nav aria-label={label}>
  {#each items as item (item.value)}
    <button
      type="button"
      aria-current={item.value === current ? 'page' : undefined}
      class:active={item.value === current}
      onclick={() => onselect(item.value)}
    >
      <span class="pill">
        <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
          <path
            d={item.icon}
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </span>
      <span class="text">{item.label}</span>
    </button>
  {/each}
</nav>

<style>
  nav {
    position: fixed;
    inset: auto 0 0 0;
    display: flex;
    justify-content: center;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-4) calc(env(safe-area-inset-bottom) + var(--space-2));
    background: color-mix(in srgb, var(--background) 88%, transparent);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border-top: 1px solid var(--outline);
    z-index: 10;
  }
  button {
    flex: 1;
    max-width: 120px;
    min-height: 56px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    border: 0;
    background: none;
    color: var(--on-surface-variant);
    font-size: 0.75rem;
    font-weight: 600;
    cursor: pointer;
  }
  .pill {
    display: grid;
    place-items: center;
    width: 56px;
    height: 30px;
    border-radius: 999px;
    transition: background var(--duration-short);
  }
  .active {
    color: var(--on-background);
  }
  .active .pill {
    background: var(--primary);
    color: var(--on-primary);
  }
</style>
