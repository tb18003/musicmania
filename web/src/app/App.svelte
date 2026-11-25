<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import InstallGuide from '$features/install/InstallGuide.svelte'
  import SettingsScreen from '$features/settings/SettingsScreen.svelte'
  import WelcomeScreen from '$features/welcome/WelcomeScreen.svelte'
  import { provideUi } from '$lib/ui/context'
  import type { InstallPlatform } from '$lib/ui/platform'
  import { applyToDocument, type Preferences } from '$lib/ui/preferences.svelte'
  import type { Router } from './router.svelte'

  interface Props {
    preferences: Preferences
    router: Router
    platform: InstallPlatform
    installed: boolean
    version: string
  }

  let { preferences, router, platform, installed, version }: Props = $props()
  // svelte-ignore state_referenced_locally
  provideUi(preferences)

  $effect(() => applyToDocument(preferences, document.documentElement))
</script>

<div class="shell">
  {#if router.current === '/install'}
    <InstallGuide {platform} {installed} onback={() => router.back()} />
  {:else if router.current === '/settings'}
    <SettingsScreen {version} onback={() => router.back()} />
  {:else}
    <WelcomeScreen
      {installed}
      oninstall={() => router.navigate('/install')}
      onsettings={() => router.navigate('/settings')}
    />
  {/if}
</div>

<style>
  /* Content stays clear of the iPhone notch and home indicator in standalone mode. */
  .shell {
    max-width: 560px;
    margin: 0 auto;
    padding: calc(env(safe-area-inset-top) + var(--space-4)) calc(env(safe-area-inset-right) + var(--space-4))
      calc(env(safe-area-inset-bottom) + var(--space-6)) calc(env(safe-area-inset-left) + var(--space-4));
  }
</style>
