# ADR 0009 — Monorepo layout and parallel CI behind one required check

- Status: Accepted · Date: 2026-11-24

## Context
MusicMania is gaining a backend: an API and a cloud database schema, versioned next to the app.
The Android project lived at the repository root, and CI ran every check sequentially in one
job (~30 min per pull request, close to its timeout).

## Decision
**Layout.** One repository, one top-level directory per deliverable:
`mobile/` (Android Gradle project, moved with `git mv` to keep history), `api/` and `database/`
(added by their own PRs), `config/` (Detekt config and license header shared by every Kotlin
project; `.editorconfig` stays at the root so editors find it), `docs/`. The build-logic resolves
shared files with `repoFile(…)`, relative to the repository root.

**CI** (`.github/workflows/ci.yml`):
- A `changes` job diffs the pull request against its base; jobs of untouched parts are skipped.
  Pushes to `develop`/`master` and manual runs check everything.
- Mobile checks run as parallel jobs: static analysis (Spotless, Detekt, Lint), unit tests +
  coverage, screenshot tests, build (debug, release with R8, baseline profile module) and
  instrumented tests on an emulator. Common setup lives in `.github/actions/setup-mobile`.
- A final `CI success` job aggregates every result (skipped counts as passed). It is the **only
  required status check**, so adding, renaming or skipping jobs never blocks merges by accident
  (path-filtered required checks would wait forever for jobs that never ran).

## Consequences
+ Wall-clock time per PR is the slowest job instead of the sum of all of them.
+ Docs-only PRs finish in seconds; API/database PRs will not rebuild the app.
− Every parallel job configures Gradle and compiles what it needs (mitigated by the Gradle cache);
  total runner minutes grow, which is free for this public repository.
− Commands now run from `mobile/`, and Android Studio must open `mobile/`.
