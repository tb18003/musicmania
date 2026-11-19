# ADR 0008 — Detekt 2 for static analysis

- Status: Accepted · Date: 2026-11-19

## Context
Detekt 1.23.8 (the last 1.x release) calls Gradle APIs removed in Gradle 10
(`ReportingExtension.file`) and 11 (`Configuration.setVisible`), and embeds Kotlin 2.0, which
forced a resolution pin on its classpath while the project compiles with Kotlin 2.4.

## Decision
Move to **Detekt 2.0.0-alpha.6** (`dev.detekt`, published August 2026). No 2.0 stable exists yet.

- Plugin id/coordinates `dev.detekt`; `DetektExtension` now uses lazy properties.
- The Kotlin 2.0 classpath pin is removed.
- `config/detekt/detekt.yml` migrated to the 2.0 keys (`threshold*` → `allowed*`,
  `UnusedPrivateMember` → `UnusedPrivateFunction`). Values keep the 1.x behaviour: where the 2.0
  defaults moved from `>=` to `>` (ComplexCondition, LongParameterList) ours were lowered by one;
  LongMethod and TooManyFunctions kept their semantics and values.
- `./gradlew detekt` keeps the light (PSI-only) analysis, the same depth as 1.x without type
  resolution. Type-aware rules (`detektMain`, full analysis) can be evaluated later.

## Consequences
+ No Gradle 10 blockers; one Kotlin compiler fewer to keep aligned.
− Alpha: rule names and keys may still change before 2.0.0. `config.validation: true` makes any
  rename fail the build loudly instead of silently disabling a rule.
− The alpha plugin itself still calls `Configuration.setVisible` (removed in Gradle 11).
