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
  import ImportScreen from '$features/importer/ImportScreen.svelte'
  import { ImportViewModel } from '$features/importer/ImportViewModel.svelte'
  import InstallGuide from '$features/install/InstallGuide.svelte'
  import LeaderboardScreen from '$features/leaderboard/LeaderboardScreen.svelte'
  import { LeaderboardViewModel } from '$features/leaderboard/LeaderboardViewModel.svelte'
  import { CloudSettingsViewModel } from '$features/settings/CloudSettingsViewModel.svelte'
  import SettingsScreen from '$features/settings/SettingsScreen.svelte'
  import TopScreen from '$features/top/TopScreen.svelte'
  import { TopViewModel } from '$features/top/TopViewModel.svelte'
  import WelcomeScreen from '$features/welcome/WelcomeScreen.svelte'
  import type { AppError } from '$lib/model/result'
  import BottomNav from '$lib/ui/components/BottomNav.svelte'
  import { provideUi } from '$lib/ui/context'
  import type { MessageKey } from '$lib/ui/i18n/en'
  import type { InstallPlatform } from '$lib/ui/platform'
  import { applyToDocument, type Preferences } from '$lib/ui/preferences.svelte'
  import type { Container } from './container'
  import { exportHistory } from './export'
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
  const {
    auth,
    history,
    remote,
    sync,
    top,
    importer,
    cloud,
    leaderboard,
    publish,
    spotifyUserId,
    session: sessionRepository,
  } = container
  // svelte-ignore state_referenced_locally
  const home = new HomeViewModel({ history, remote, sync, runSync, now: container.now })
  const historyVm = new HistoryViewModel(history)
  const topVm = new TopViewModel(top)
  const rankingVm = new LeaderboardViewModel({ cloud, leaderboard, publish, spotifyUserId })
  const cloudSettingsVm = new CloudSettingsViewModel({ cloud, publish, spotifyUserId })
  let rankingLoaded = false
  let cloudSettingsLoaded = false
  let topLoaded = false
  // After an import: every screen that reads local history recomputes.
  const importVm = new ImportViewModel(importer, () => {
    void home.load()
    void historyVm.refresh()
    if (topLoaded) void topVm.load()
  })

  // Screens that call Spotify or the API load the first time they are opened.
  $effect(() => {
    if (session !== 'signedIn') return
    if (router.current === '/top' && !topLoaded) {
      topLoaded = true
      void topVm.load()
    }
    if (router.current === '/ranking' && !rankingLoaded && cloud.enabled) {
      rankingLoaded = true
      void rankingVm.load()
    }
    if (router.current === '/settings' && !cloudSettingsLoaded && cloud.enabled) {
      cloudSettingsLoaded = true
      void cloudSettingsVm.load()
    }
  })

  async function copy(text: string): Promise<boolean> {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      return false
    }
  }

  /** Every sync goes through here, so an expired session always lands back on the welcome screen. */
  async function runSync(force: boolean): Promise<AppError | null> {
    const result = await sync.run({ force })
    if (result.ok) {
      // Like CloudSyncObserver: fresh plays → fresh aggregates for the ranking, if I share.
      // Failures are fine to ignore: the next sync publishes again.
      if (!result.data.skipped && cloud.enabled) void publish.run()
      return null
    }
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

  type Tab = Extract<Route, '/' | '/top' | '/ranking' | '/history' | '/settings'>
  const tabs = $derived<{ value: Tab; label: string; icon: string }[]>([
    { value: '/', label: preferences.t('nav_home'), icon: 'M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z' },
    { value: '/top', label: preferences.t('nav_top'), icon: 'M6 20V11M12 20V4M18 20v-6' },
    ...(cloud.enabled
      ? [
          {
            value: '/ranking' as const,
            label: preferences.t('nav_ranking'),
            icon: 'M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4',
          },
        ]
      : []),
    { value: '/history', label: preferences.t('nav_history'), icon: 'M12 7v5l3 2M21 12a9 9 0 1 1-9-9 9 9 0 0 1 9 9z' },
    {
      value: '/settings',
      label: preferences.t('nav_settings'),
      icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15l1.6 1-2 3.4-1.8-.7a7 7 0 0 1-2 1.2L15 22h-4l-.3-2.1a7 7 0 0 1-2-1.2l-1.8.7-2-3.4 1.6-1a7 7 0 0 1 0-2.4L4.9 11l2-3.4 1.8.7a7 7 0 0 1 2-1.2L11 5h4l.3 2.1a7 7 0 0 1 2 1.2l1.8-.7 2 3.4-1.6 1a7 7 0 0 1 0 2.4z',
    },
  ])
  const currentTab = $derived<Tab>(
    router.current === '/top' ||
      router.current === '/ranking' ||
      router.current === '/history' ||
      router.current === '/settings'
      ? router.current
      : '/',
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
  {:else if router.current === '/top'}
    <TopScreen
      state={topVm.state}
      {now}
      onkind={(kind) => topVm.select(kind)}
      onrange={(range) => topVm.selectRange(range)}
      onrefresh={() => topVm.refresh()}
      onimport={() => router.navigate('/import')}
    />
  {:else if router.current === '/ranking' && cloud.enabled}
    <LeaderboardScreen
      state={rankingVm.state}
      onmetric={(metric) => rankingVm.selectMetric(metric)}
      onperiod={(period) => rankingVm.selectPeriod(period)}
      onenablesharing={() => rankingVm.enableSharing()}
      onfriend={(id) => rankingVm.toggleFriend(id)}
      onretry={() => rankingVm.load()}
      oncopy={copy}
    />
  {:else if router.current === '/import'}
    <ImportScreen state={importVm.state} onfiles={(files) => importVm.start(files)} onback={() => router.back()} />
  {:else if router.current === '/history'}
    <HistoryScreen state={historyVm.state} {now} onloadmore={() => historyVm.loadMore()} />
  {:else if router.current === '/settings'}
    <SettingsScreen
      {version}
      account={{ displayName: home.state.profile?.displayName ?? null }}
      onsignout={signOut}
      onimport={() => router.navigate('/import')}
      onexport={() => exportHistory(history, Date.now())}
      cloud={cloud.enabled ? cloudSettingsVm.state : null}
      onsharing={(enabled) => cloudSettingsVm.setSharing(enabled)}
      ondeletecloud={() => cloudSettingsVm.deleteData()}
      oncopy={copy}
    />
  {:else}
    <HomeScreen state={home.state} {now} onsync={() => home.syncNow()} onimport={() => router.navigate('/import')} />
  {/if}
</div>

{#if session === 'signedIn' && router.current !== '/install' && router.current !== '/import'}
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
