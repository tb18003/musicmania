<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import Button from '$lib/ui/components/Button.svelte'
  import Card from '$lib/ui/components/Card.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import SegmentedControl from '$lib/ui/components/SegmentedControl.svelte'
  import { useUi } from '$lib/ui/context'
  import { entrances } from '$lib/ui/motion'
  import type { LanguagePreference, MotionPreference, ThemePreference } from '$lib/ui/preferences.svelte'

  interface Props {
    version: string
    /** Signed-in account, or null when signed out. */
    account: { displayName: string | null } | null
    /** Shown as a back button when the screen is reached from the welcome screen. */
    onback?: () => void
    onsignout?: () => void
    onimport?: () => void
    onexport?: () => void
  }

  let { version, account, onback, onsignout, onimport, onexport }: Props = $props()

  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('settings')

  function signOut() {
    // Destructive and irreversible: always confirm (native dialog, accessible everywhere).
    if (window.confirm(ui.t('settings_sign_out_confirm'))) onsignout?.()
  }

  const themes = $derived<{ value: ThemePreference; label: string }[]>([
    { value: 'system', label: ui.t('settings_theme_system') },
    { value: 'light', label: ui.t('settings_theme_light') },
    { value: 'dark', label: ui.t('settings_theme_dark') },
  ])
  const languages = $derived<{ value: LanguagePreference; label: string }[]>([
    { value: 'system', label: ui.t('settings_language_system') },
    { value: 'en', label: ui.t('settings_language_en') },
    { value: 'es', label: ui.t('settings_language_es') },
  ])
  const motions = $derived<{ value: MotionPreference; label: string }[]>([
    { value: 'system', label: ui.t('settings_motion_system') },
    { value: 'reduce', label: ui.t('settings_motion_reduce') },
    { value: 'full', label: ui.t('settings_motion_full') },
  ])
</script>

<main>
  <ScreenHeader title={ui.t('settings_title')} backLabel={ui.t('common_back')} {onback} />
  <div class="cards">
    {#if account}
      <Card title={ui.t('settings_account')} {play} index={0}>
        {#if account.displayName}
          <p>{ui.t('settings_signed_in_as', { name: account.displayName })}</p>
        {/if}
        <Button variant="tonal" onclick={signOut}>{ui.t('settings_sign_out')}</Button>
      </Card>
      <Card title={ui.t('settings_data')} {play} index={0}>
        <div class="buttons">
          <Button variant="tonal" onclick={() => onimport?.()}>{ui.t('settings_import')}</Button>
          <Button variant="tonal" onclick={() => onexport?.()}>{ui.t('settings_export')}</Button>
        </div>
      </Card>
    {/if}
    <Card title={ui.t('settings_appearance')} {play} index={1}>
      <SegmentedControl
        label={ui.t('settings_theme')}
        options={themes}
        value={ui.theme}
        onchange={(value) => ui.setTheme(value)}
      />
      <SegmentedControl
        label={ui.t('settings_language')}
        options={languages}
        value={ui.language}
        onchange={(value) => ui.setLanguage(value)}
      />
      <SegmentedControl
        label={ui.t('settings_motion')}
        options={motions}
        value={ui.motion}
        onchange={(value) => ui.setMotion(value)}
      />
    </Card>
    <Card title={ui.t('settings_about')} {play} index={2}>
      <p>{ui.t('settings_version', { version })}</p>
      <p>
        <a href="https://github.com/tb18003/musicmania" target="_blank" rel="noopener noreferrer">
          {ui.t('settings_source')}
        </a>
      </p>
    </Card>
  </div>
</main>

<style>
  .cards {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }
  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
  }
  p {
    margin: 0 0 var(--space-2);
    color: var(--on-surface-variant);
  }
  a {
    display: inline-flex;
    align-items: center;
    min-height: var(--touch);
  }
</style>
