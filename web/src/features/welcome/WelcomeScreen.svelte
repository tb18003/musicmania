<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import BrandMark from '$lib/ui/components/BrandMark.svelte'
  import Button from '$lib/ui/components/Button.svelte'
  import Card from '$lib/ui/components/Card.svelte'
  import { useUi } from '$lib/ui/context'
  import { entrance, entrances } from '$lib/ui/motion'

  interface Props {
    /** Already running from the home screen: no need to explain how to install. */
    installed: boolean
    oninstall: () => void
    onsettings: () => void
  }

  let { installed, oninstall, onsettings }: Props = $props()
  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('welcome')
</script>

<main>
  <div class="hero" use:entrance={{ play, index: 0 }}>
    <BrandMark size={88} />
    <h1><span class="brand">{ui.t('app_name')}</span></h1>
    <p class="title">{ui.t('welcome_title')}</p>
    <p class="subtitle">{ui.t('welcome_subtitle')}</p>
  </div>

  <div class="actions" use:entrance={{ play, index: 1 }}>
    <Button disabled>{ui.t('welcome_sign_in')}</Button>
    <p class="hint">{ui.t('welcome_sign_in_soon')}</p>
  </div>

  {#if !installed}
    <Card title={ui.t('welcome_install_title')} {play} index={2}>
      <p class="card-text">{ui.t('welcome_install_message')}</p>
      <Button variant="tonal" onclick={oninstall}>{ui.t('welcome_install_action')}</Button>
    </Card>
  {/if}

  <p class="privacy" use:entrance={{ play, index: 3 }}>{ui.t('welcome_privacy')}</p>
  <div class="footer">
    <Button variant="text" onclick={onsettings}>{ui.t('welcome_settings')}</Button>
  </div>
</main>

<style>
  main {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    padding-top: var(--space-6);
  }
  .hero {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }
  h1 {
    font-size: 2.6rem;
    letter-spacing: -0.02em;
  }
  .brand {
    background: var(--brand-gradient);
    background-clip: text;
    -webkit-background-clip: text;
    color: transparent;
  }
  .title {
    font-family: var(--font-display);
    font-size: 1.4rem;
    font-weight: 600;
    margin: 0;
  }
  .subtitle,
  .hint,
  .privacy,
  .card-text {
    color: var(--on-surface-variant);
    margin: 0;
  }
  .card-text {
    margin-bottom: var(--space-4);
  }
  .actions {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .hint,
  .privacy {
    font-size: 0.9rem;
  }
  .footer {
    display: flex;
    justify-content: center;
  }
</style>
