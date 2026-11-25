<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import Card from '$lib/ui/components/Card.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import { useUi } from '$lib/ui/context'
  import { entrances } from '$lib/ui/motion'
  import type { InstallPlatform } from '$lib/ui/platform'

  interface Props {
    platform: InstallPlatform
    installed: boolean
    onback: () => void
  }

  let { platform, installed, onback }: Props = $props()
  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('install')

  const iosSteps = ['install_ios_step_1', 'install_ios_step_2', 'install_ios_step_3', 'install_ios_step_4'] as const
  const androidSteps = ['install_android_step_1', 'install_android_step_2'] as const
  // The visitor's own platform first.
  const order = $derived(platform === 'android' ? (['android', 'ios'] as const) : (['ios', 'android'] as const))
</script>

<main>
  <ScreenHeader
    title={ui.t('install_title')}
    subtitle={ui.t('install_subtitle')}
    backLabel={ui.t('common_back')}
    {onback}
  />

  {#if installed}
    <p class="installed" role="status">{ui.t('install_already')}</p>
  {/if}

  <div class="cards">
    {#each order as kind, index (kind)}
      {@const steps = kind === 'ios' ? iosSteps : androidSteps}
      <Card title={ui.t(kind === 'ios' ? 'install_ios_title' : 'install_android_title')} {play} {index}>
        <ol>
          {#each steps as step (step)}
            <li>{ui.t(step)}</li>
          {/each}
        </ol>
      </Card>
    {/each}
    <Card title={ui.t('install_why_title')} {play} index={2}>
      <p>{ui.t('install_why_message')}</p>
    </Card>
  </div>
</main>

<style>
  .cards {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }
  ol {
    margin: 0;
    padding-left: var(--space-5);
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  p {
    margin: 0;
    color: var(--on-surface-variant);
  }
  .installed {
    margin-bottom: var(--space-4);
    color: var(--positive);
    font-weight: 600;
  }
</style>
