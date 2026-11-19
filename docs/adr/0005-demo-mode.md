# ADR 0005 — Demo mode backed by the real database

- Status: Accepted · Date: 2026-11-19

## Context
Spotify Development Mode limits apps to 5 users, so most people (and contributors, CI,
screenshot tests) cannot log in.

## Decision
Demo mode seeds the **real** Room database with a deterministic, fictional catalogue and ~14 months
of plays, plus top snapshots for two days (so movement arrows show). Network repositories
short-circuit while the demo flag is set. No parallel "fake" UI path exists.

## Consequences
+ Every screen, animation and query is exercised exactly as in production.
+ Deterministic data (fixed seed) keeps screenshots stable.
− Seeding takes a moment on first launch (done on the IO dispatcher).
