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
  import Notice from '$lib/ui/components/Notice.svelte'
  import type { CloudSettingsState } from './CloudSettingsViewModel.svelte'
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
    /** The ranking section; null when the ranking is not configured (or signed out). */
    cloud?: CloudSettingsState | null
    onsharing?: (enabled: boolean) => void
    ondeletecloud?: () => void
    oncopy?: (text: string) => Promise<boolean>
  }

  let {
    version,
    account,
    onback,
    onsignout,
    onimport,
    onexport,
    cloud = null,
    onsharing,
    ondeletecloud,
    oncopy,
  }: Props = $props()

  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('settings')

  function deleteCloud() {
    if (window.confirm(ui.t('settings_cloud_delete_confirm'))) ondeletecloud?.()
  }

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
      {#if cloud?.loaded}
        <Card title={ui.t('settings_cloud')} {play} index={0}>
          {#if cloud.notAllowlisted}
            <p>{ui.t('settings_cloud_not_allowlisted')}</p>
          {:else}
            <label class="switch">
              <span>
                <strong>{ui.t('settings_cloud_sharing')}</strong>
                <small>{ui.t('settings_cloud_sharing_body')}</small>
              </span>
              <input
                type="checkbox"
                role="switch"
                checked={cloud.sharingEnabled}
                disabled={cloud.busy}
                onchange={(event) => onsharing?.(event.currentTarget.checked)}
              />
            </label>
          {/if}
          {#if cloud.spotifyUserId}
            <p class="id">
              {ui.t('settings_cloud_spotify_id', { id: cloud.spotifyUserId })}
              <Button variant="text" onclick={() => cloud.spotifyUserId && oncopy?.(cloud.spotifyUserId)}>
                {ui.t('ranking_copy_id')}
              </Button>
            </p>
          {/if}
          {#if !cloud.notAllowlisted}
            <Button variant="tonal" disabled={cloud.busy} onclick={deleteCloud}>{ui.t('settings_cloud_delete')}</Button>
          {/if}
          {#if cloud.message}
            <div class="message">
              <Notice tone={cloud.message === 'settings_cloud_failed' ? 'error' : 'info'}>{ui.t(cloud.message)}</Notice>
            </div>
          {/if}
        </Card>
      {/if}
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
  .switch {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-4);
    min-height: var(--touch);
    margin-bottom: var(--space-3);
    cursor: pointer;
  }
  .switch span {
    display: flex;
    flex-direction: column;
  }
  .switch small {
    color: var(--on-surface-variant);
    font-size: 0.85rem;
  }
  .switch input {
    flex: none;
    appearance: none;
    -webkit-appearance: none;
    width: 52px;
    height: 32px;
    margin: 0;
    border-radius: 999px;
    background: var(--surface-highest);
    position: relative;
    cursor: pointer;
    transition: background var(--duration-short);
  }
  .switch input::after {
    content: '';
    position: absolute;
    top: 4px;
    left: 4px;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--on-surface-variant);
    transition: transform var(--duration-short) var(--easing-emphasized);
  }
  .switch input:checked {
    background: var(--primary);
  }
  .switch input:checked::after {
    transform: translateX(20px);
    background: var(--on-primary);
  }
  .id {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2);
    word-break: break-all;
  }
  .message {
    margin-top: var(--space-3);
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
