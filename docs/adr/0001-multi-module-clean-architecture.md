# ADR 0001 — Multi-module Clean Architecture with MVVM/UDF

- Status: Accepted · Date: 2026-11-19

## Context
The app must stay maintainable as features grow, be testable without devices, and allow
contributors to work on features in parallel.

## Decision
- Gradle multi-module layout inspired by *Now in Android*: `app`, `core:*`, `feature:*`,
  `sync:work`, `widget`, with shared configuration in `build-logic` convention plugins.
- Clean Architecture: repository **interfaces in `core:domain`** (pure Kotlin), implementations
  `internal` in `core:data`, wired with Hilt.
- MVVM with unidirectional data flow: immutable `UiState` exposed as `StateFlow`, stateless
  Composables, one-shot events through `Channel`.
- Features never depend on each other or on data-layer modules; the `app` module wires navigation.
  Rules are enforced by Konsist tests.

## Consequences
+ Fast incremental builds, isolated tests, clear ownership.
+ Pure-Kotlin domain is trivially unit-testable.
− More modules and boilerplate (mitigated by convention plugins).
