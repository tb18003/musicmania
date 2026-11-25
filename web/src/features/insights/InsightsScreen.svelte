<!--
  MusicMania - open-source Spotify listening statistics for the web.
  Copyright (C) 2026 MusicMania contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->
<script lang="ts">
  import { listenedChange, nextGoals, peakHour } from '$lib/domain/insights'
  import type { TimeRange } from '$lib/model/top'
  import ClockChart from '$lib/ui/charts/ClockChart.svelte'
  import Heatmap from '$lib/ui/charts/Heatmap.svelte'
  import RateRing from '$lib/ui/charts/RateRing.svelte'
  import Button from '$lib/ui/components/Button.svelte'
  import Card from '$lib/ui/components/Card.svelte'
  import ScreenHeader from '$lib/ui/components/ScreenHeader.svelte'
  import SegmentedControl from '$lib/ui/components/SegmentedControl.svelte'
  import StatTile from '$lib/ui/components/StatTile.svelte'
  import { useUi } from '$lib/ui/context'
  import { formatNumber } from '$lib/ui/format'
  import type { MessageKey } from '$lib/ui/i18n/en'
  import { entrances } from '$lib/ui/motion'
  import type { InsightsState } from './InsightsViewModel.svelte'

  interface Props {
    state: InsightsState
    onrange: (range: TimeRange) => void
    onimport: () => void
  }

  // Renamed locally: a binding called `state` would turn the `$state` rune into a store read.
  let { state: view, onrange, onimport }: Props = $props()
  const ui = useUi()
  const play = !ui.reduceMotion && entrances.shouldPlay('insights')
  const number = (value: number) => formatNumber(value, ui.locale)
  const percent = (fraction: number) =>
    new Intl.NumberFormat(ui.locale, { style: 'percent', maximumFractionDigits: 0 }).format(fraction)
  const hourLabel = (hour: number) =>
    new Intl.DateTimeFormat(ui.locale, { hour: 'numeric' }).format(new Date(2026, 0, 5, hour))
  /** Monday first; 5 January 2026 is a Monday. */
  const weekdays = $derived(
    Array.from({ length: 7 }, (_, i) =>
      new Intl.DateTimeFormat(ui.locale, { weekday: 'short' }).format(new Date(2026, 0, 5 + i)),
    ),
  )
  const weekdayLong = (day: number) =>
    new Intl.DateTimeFormat(ui.locale, { weekday: 'long' }).format(new Date(2026, 0, 5 + day))

  const ranges = $derived<{ value: TimeRange; label: string }[]>([
    { value: 'FOUR_WEEKS', label: ui.t('top_range_four_weeks') },
    { value: 'SIX_MONTHS', label: ui.t('top_range_six_months') },
    { value: 'ONE_YEAR', label: ui.t('top_range_one_year') },
    { value: 'LIFETIME', label: ui.t('top_range_lifetime') },
  ])

  const insights = $derived(view.insights)
  const peak = $derived(insights ? peakHour(insights.clock) : null)
  /** The busiest weekday × hour cell, for the heatmap's spoken description. */
  const busiest = $derived.by(() => {
    let best = { day: 0, hour: 0, ms: 0 }
    insights?.heatmap.forEach((row, day) =>
      row.forEach((ms, hour) => {
        if (ms > best.ms) best = { day, hour, ms }
      }),
    )
    return best
  })
  const change = $derived(insights ? listenedChange(insights.comparison) : null)
  const goals = $derived(insights ? nextGoals(insights.milestones) : [])
  const achieved = $derived(insights?.milestones.filter((m) => m.achieved).length ?? 0)
  const platformTotal = $derived(insights?.habits.platforms.reduce((sum, p) => sum + p.listenedMs, 0) ?? 0)
</script>

<main>
  <ScreenHeader title={ui.t('insights_title')} subtitle={ui.t('insights_subtitle')} />
  <SegmentedControl label={ui.t('top_range')} options={ranges} value={view.range} onchange={onrange} />

  {#if insights && !insights.hasHistory}
    <Card title={ui.t('insights_empty_title')}>
      <p class="muted">{ui.t('insights_empty_message')}</p>
      <Button variant="tonal" onclick={onimport}>{ui.t('home_import_action')}</Button>
    </Card>
  {:else if insights}
    <div class="cards" aria-busy={view.loading}>
      {#if insights.personality}
        <Card title={ui.t('insights_personality')} {play} index={0}>
          <p class="personality">{ui.t(`insights_personality_${insights.personality}` as MessageKey)}</p>
          <p class="muted">{ui.t(`insights_personality_${insights.personality}_body` as MessageKey)}</p>
        </Card>
      {/if}

      <Card title={ui.t('insights_clock')} {play} index={1}>
        {#if peak === null}
          <p class="muted">{ui.t('insights_clock_empty')}</p>
        {:else}
          <ClockChart
            clock={insights.clock}
            {peak}
            {play}
            description={ui.t('insights_clock_description', { hour: hourLabel(peak) })}
          />
        {/if}
      </Card>

      {#if busiest.ms > 0}
        <Card title={ui.t('insights_heatmap')} {play} index={2}>
          <Heatmap
            grid={insights.heatmap}
            {weekdays}
            {play}
            description={ui.t('insights_heatmap_description', {
              day: weekdayLong(busiest.day),
              hour: hourLabel(busiest.hour),
            })}
          />
        </Card>
      {/if}

      <div class="grid">
        <StatTile
          label={ui.t('insights_streak')}
          value={number(insights.streaks.current)}
          unit={ui.tp('home_days_unit', insights.streaks.current)}
          detail={`${ui.t('insights_longest_streak')}: ${number(insights.streaks.longest)}`}
          {play}
          index={3}
        />
        <StatTile
          label={ui.t('insights_comparison')}
          value={change === null ? '—' : percent(Math.abs(change))}
          detail={change === null
            ? ui.t('insights_comparison_none')
            : ui.t(change >= 0 ? 'insights_comparison_up' : 'insights_comparison_down', {
                percent: percent(Math.abs(change)),
              })}
          {play}
          index={4}
        />
      </div>

      <Card title={ui.t('insights_habits')} {play} index={5}>
        {#if insights.habits.skipRate === null && insights.habits.shuffleRate === null}
          <p class="muted">{ui.t('insights_habits_unknown')}</p>
        {:else}
          <div class="rings">
            {#if insights.habits.skipRate !== null}
              <RateRing
                label={ui.t('insights_skip_rate')}
                rate={insights.habits.skipRate}
                percent={percent(insights.habits.skipRate)}
                description={ui.t('insights_rate_description', {
                  label: ui.t('insights_skip_rate'),
                  percent: percent(insights.habits.skipRate),
                })}
              />
            {/if}
            {#if insights.habits.shuffleRate !== null}
              <RateRing
                label={ui.t('insights_shuffle_rate')}
                rate={insights.habits.shuffleRate}
                percent={percent(insights.habits.shuffleRate)}
                description={ui.t('insights_rate_description', {
                  label: ui.t('insights_shuffle_rate'),
                  percent: percent(insights.habits.shuffleRate),
                })}
              />
            {/if}
          </div>
        {/if}
        {#if insights.habits.platforms.length > 0}
          <h3>{ui.t('insights_platforms')}</h3>
          <ul class="platforms">
            {#each insights.habits.platforms.slice(0, 5) as platform (platform.platform)}
              {@const share = platform.listenedMs / Math.max(1, platformTotal)}
              <li>
                <span class="name">{platform.platform}</span>
                <span class="bar" aria-hidden="true"><span style:width="{share * 100}%"></span></span>
                <span class="value">{percent(share)}</span>
              </li>
            {/each}
          </ul>
        {/if}
      </Card>

      <Card title={ui.t('recap_open_title')} {play} index={6}>
        <p class="muted">{ui.t('recap_open_body')}</p>
        <a class="cta" href="#/recap">{ui.t('recap_open')}</a>
      </Card>

      <Card title={ui.t('insights_milestones')} {play} index={7}>
        <p class="muted">{ui.tp('insights_milestones_achieved', achieved)}</p>
        <ul class="goals">
          {#each goals as goal (goal.type)}
            <li>
              <span>
                {ui.t(`insights_milestone_${goal.type}` as MessageKey, {
                  value: number(goal.value),
                  threshold: number(goal.threshold),
                })}
              </span>
              <progress max="1" value={goal.progress}>{percent(goal.progress)}</progress>
            </li>
          {/each}
        </ul>
      </Card>
    </div>
  {/if}
</main>

<style>
  .cards {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }
  .cards[aria-busy='true'] {
    opacity: 0.6;
  }
  .cta {
    display: inline-flex;
    align-items: center;
    min-height: var(--touch);
    padding: 0 var(--space-5);
    border-radius: 999px;
    background: var(--primary);
    color: var(--on-primary);
    font-family: var(--font-display);
    font-weight: 600;
    text-decoration: none;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-3);
  }
  .muted {
    margin: 0 0 var(--space-3);
    color: var(--on-surface-variant);
  }
  .personality {
    margin: 0 0 var(--space-1);
    font-family: var(--font-display);
    font-size: 1.6rem;
    font-weight: 700;
    background: var(--brand-gradient);
    background-clip: text;
    -webkit-background-clip: text;
    color: transparent;
  }
  .rings {
    display: flex;
    justify-content: space-around;
    margin-bottom: var(--space-3);
  }
  h3 {
    font-size: 0.95rem;
    margin: var(--space-2) 0;
  }
  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .platforms li {
    display: grid;
    grid-template-columns: 7rem 1fr 3rem;
    align-items: center;
    gap: var(--space-2);
    font-size: 0.9rem;
  }
  .bar {
    height: 8px;
    border-radius: 999px;
    background: var(--surface-highest);
    overflow: hidden;
  }
  .bar span {
    display: block;
    height: 100%;
    background: var(--secondary);
  }
  .value {
    text-align: right;
    color: var(--on-surface-variant);
  }
  .goals li {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    font-size: 0.9rem;
  }
  progress {
    width: 100%;
    height: 8px;
    appearance: none;
    border: 0;
    border-radius: 999px;
    background: var(--surface-highest);
    overflow: hidden;
  }
  progress::-webkit-progress-bar {
    background: var(--surface-highest);
  }
  progress::-webkit-progress-value {
    background: var(--primary);
    border-radius: 999px;
  }
  progress::-moz-progress-bar {
    background: var(--primary);
  }
</style>
