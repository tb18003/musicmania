<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts" generics="T extends string">
  interface Props {
    label: string
    options: readonly { value: T; label: string }[]
    value: T
    onchange: (value: T) => void
  }

  let { label, options, value, onchange }: Props = $props()
  const id = $props.id()
</script>

<!-- A native radio group: keyboard, screen readers and iOS VoiceOver work out of the box. -->
<fieldset>
  <legend>{label}</legend>
  <div class="segments">
    {#each options as option (option.value)}
      <label class:selected={option.value === value}>
        <input
          type="radio"
          name={id}
          value={option.value}
          checked={option.value === value}
          onchange={() => onchange(option.value)}
        />
        {option.label}
      </label>
    {/each}
  </div>
</fieldset>

<style>
  fieldset {
    border: 0;
    margin: 0 0 var(--space-4);
    padding: 0;
  }
  legend {
    padding: 0;
    margin-bottom: var(--space-2);
    color: var(--on-surface-variant);
    font-size: 0.9rem;
  }
  .segments {
    display: flex;
    gap: var(--space-1);
    padding: var(--space-1);
    border-radius: 999px;
    background: var(--surface-high);
  }
  label {
    flex: 1;
    min-height: var(--touch);
    display: grid;
    place-items: center;
    border-radius: 999px;
    font-weight: 600;
    cursor: pointer;
    text-align: center;
    transition: background var(--duration-short);
  }
  label.selected {
    background: var(--primary);
    color: var(--on-primary);
  }
  label:has(:focus-visible) {
    outline: 3px solid var(--primary);
    outline-offset: 2px;
  }
  input {
    position: absolute;
    opacity: 0;
    pointer-events: none;
  }
</style>
