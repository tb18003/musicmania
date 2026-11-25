<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { onMount } from 'svelte'
  import HistoryScreen from '$features/history/HistoryScreen.svelte'
  import { HistoryViewModel } from '$features/history/HistoryViewModel.svelte'
  import HomeScreen from '$features/home/HomeScreen.svelte'
  import { HomeViewModel } from '$features/home/HomeViewModel.svelte'
  import InstallGuide from '$features/install/InstallGuide.svelte'
  import SettingsScreen from '$features/settings/SettingsScreen.svelte'
  import WelcomeScreen from '$features/welcome/WelcomeScreen.svelte'
  import type { AppError } from '$lib/model/result'
  import BottomNav from '$lib/ui/components/BottomNav.svelte'
  import { provideUi } from '$lib/ui/context'
  import type { MessageKey } from '$lib/ui/i18n/en'
  import type { InstallPlatform } from '$lib/ui/platform'
  import { applyToDocument, type Preferences } from '$lib/ui/preferences.svelte'
  import type { Container } from './container'
  import type { Route, Router } from './router.svelte'

  interface Props {
    preferences: Preferences
    router: Router
    container: Container
    platform: InstallPlatform
    installed: boolean
    version: string
  }

  let { preferences, router, container, platform, installed, version }: Props = $props()
  // svelte-ignore state_referenced_locally
  provideUi(preferences)
  $effect(() => applyToDocument(preferences, document.documentElement))

  let session = $state<'loading' | 'signedOut' | 'signedIn'>('loading')
  let welcomeMessage = $state<MessageKey | null>(null)
  let signingIn = $state(false)
  let now = $state(Date.now())

  // svelte-ignore state_referenced_locally
  const { auth, history, remote, sync, session: sessionRepository } = container
  // svelte-ignore state_referenced_locally
  const home = new HomeViewModel({ history, remote, sync, runSync, now: container.now })
  const historyVm = new HistoryViewModel(history)

  /** Every sync goes through here, so an expired session always lands back on the welcome screen. */
  async function runSync(force: boolean): Promise<AppError | null> {
    const result = await sync.run({ force })
    if (result.ok) return null
    if (result.error.kind === 'sessionExpired') {
      session = 'signedOut'
      welcomeMessage = 'welcome_session_expired'
    }
    return result.error
  }

  async function autoSync() {
    home.reportSyncError(await runSync(false))
  }

  onMount(() => {
    const cleanups: (() => void)[] = []
    const tick = setInterval(() => (now = Date.now()), 30_000)
    cleanups.push(() => clearInterval(tick))

    void (async () => {
      session = (await auth.isSignedIn()) ? 'signedIn' : 'signedOut'
      if (session !== 'signedIn') return
      // Ask the browser not to evict our data under storage pressure (iOS honours it for installed apps).
      void navigator.storage?.persist?.()
      await sync.restore()
      cleanups.push(home.start())
      void historyVm.refresh()
      cleanups.push(sync.subscribe(() => void historyVm.refresh()))
      void autoSync()
      // No background sync on iOS: catch up every time the app comes back to the foreground.
      const onVisible = () => {
        if (document.visibilityState === 'visible') void autoSync()
      }
      document.addEventListener('visibilitychange', onVisible)
      cleanups.push(() => document.removeEventListener('visibilitychange', onVisible))
    })()
    return () => cleanups.forEach((cleanup) => cleanup())
  })

  async function signIn() {
    signingIn = true
    try {
      window.location.assign(await auth.beginLogin())
    } catch {
      signingIn = false
      welcomeMessage = 'welcome_sign_in_failed'
    }
  }

  async function signOut() {
    await sessionRepository.signOut()
    window.location.hash = '/'
    window.location.reload()
  }

  type Tab = Extract<Route, '/' | '/history' | '/settings'>
  const tabs = $derived<{ value: Tab; label: string; icon: string }[]>([
    { value: '/', label: preferences.t('nav_home'), icon: 'M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z' },
    { value: '/history', label: preferences.t('nav_history'), icon: 'M12 7v5l3 2M21 12a9 9 0 1 1-9-9 9 9 0 0 1 9 9z' },
    {
      value: '/settings',
      label: preferences.t('nav_settings'),
      icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15l1.6 1-2 3.4-1.8-.7a7 7 0 0 1-2 1.2L15 22h-4l-.3-2.1a7 7 0 0 1-2-1.2l-1.8.7-2-3.4 1.6-1a7 7 0 0 1 0-2.4L4.9 11l2-3.4 1.8.7a7 7 0 0 1 2-1.2L11 5h4l.3 2.1a7 7 0 0 1 2 1.2l1.8-.7 2 3.4-1.6 1a7 7 0 0 1 0 2.4z',
    },
  ])
  const currentTab = $derived<Tab>(
    router.current === '/history' || router.current === '/settings' ? router.current : '/',
  )
</script>

<div class="shell" class:with-nav={session === 'signedIn'}>
  {#if router.current === '/install'}
    <InstallGuide {platform} {installed} onback={() => router.back()} />
  {:else if session === 'loading'}
    <p class="visually-hidden" role="status">…</p>
  {:else if session === 'signedOut'}
    {#if router.current === '/settings'}
      <SettingsScreen {version} account={null} onback={() => router.back()} />
    {:else}
      <WelcomeScreen
        {installed}
        canSignIn={auth.configured}
        {signingIn}
        message={welcomeMessage}
        onsignin={signIn}
        oninstall={() => router.navigate('/install')}
        onsettings={() => router.navigate('/settings')}
      />
    {/if}
  {:else if router.current === '/history'}
    <HistoryScreen state={historyVm.state} {now} onloadmore={() => historyVm.loadMore()} />
  {:else if router.current === '/settings'}
    <SettingsScreen {version} account={{ displayName: home.state.profile?.displayName ?? null }} onsignout={signOut} />
  {:else}
    <HomeScreen state={home.state} {now} onsync={() => home.syncNow()} />
  {/if}
</div>

{#if session === 'signedIn' && router.current !== '/install'}
  <BottomNav
    label={preferences.t('nav_label')}
    items={tabs}
    current={currentTab}
    onselect={(tab) => router.navigate(tab)}
  />
{/if}

<style>
  /* Content stays clear of the iPhone notch and home indicator in standalone mode. */
  .shell {
    max-width: 560px;
    margin: 0 auto;
    padding: calc(env(safe-area-inset-top) + var(--space-4)) calc(env(safe-area-inset-right) + var(--space-4))
      calc(env(safe-area-inset-bottom) + var(--space-6)) calc(env(safe-area-inset-left) + var(--space-4));
  }
  .with-nav {
    padding-bottom: calc(env(safe-area-inset-bottom) + 96px);
  }
</style>
