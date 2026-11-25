<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import Card from '$lib/ui/components/Card.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import SegmentedControl from '$lib/ui/components/SegmentedControl.svelte'
  import { useUi } from '$lib/ui/context'
  import { entrances } from '$lib/ui/motion'
  import type { LanguagePreference, MotionPreference, ThemePreference } from '$lib/ui/preferences.svelte'

  interface Props {
    version: string
    onback: () => void
  }

  let { version, onback }: Props = $props()
  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('settings')

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
    <Card title={ui.t('settings_appearance')} {play} index={0}>
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
    <Card title={ui.t('settings_about')} {play} index={1}>
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
