# Contributing to MusicMania

Thanks for helping! This guide explains how we work. The technical reference (architecture,
conventions, security rules) is [`CLAUDE.md`](CLAUDE.md) — please read it first.

## Branching model

```
master   ●───────────────●──────────── releases only (tags vX.Y.Z)
          \             / \
develop    ●──●──●──●──●───●──●─────── integration branch (default for PRs)
               \    /
feature/x       ●──●                   your work
```

- Create your branch from `develop`: `feature/<name>`, `fix/<name>`, `docs/<name>`, `chore/<name>`.
- Open a pull request **into `develop`**. CI must be green and one review is required.
- Releases: `release/x.y.z` from `develop` → PR to `master` → tag `vx.y.z` → merge back to `develop`.
- Hotfixes: `hotfix/x.y.z` from `master` → PR to `master` and `develop`.
- Versions: `web/package.json` holds the one release version (Android's `versionName`/`versionCode` and the
  API's announced version derive from it, [ADR 0013](docs/adr/0013-single-release-version-and-update-check.md)).
  To release, bump it (`npm version <x.y.z> --no-git-tag-version` in `web/`), then tag `web-vx.y.z`: the web is
  deployed and the signed APK is attached to the GitHub release ([setup](docs/setup/android-release.md)).
  Then tag `api-vx.y.z`: Android users are offered the update once the API is deployed.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`

`feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`. Scope = module or area,
e.g. `feat(insights): add weekday heatmap`. Keep commits small; each one must build.

## Before opening a PR

```bash
cd mobile
./gradlew spotlessApply                                   # format + license headers
./gradlew spotlessCheck detekt testDebugUnitTest test assembleDebug lintDebug
```

- Add or update tests for every behaviour change (bug fixes start with a failing test).
- UI strings go to `values/strings.xml` **and** `values-es/strings.xml`.
- Update screenshots when UI changes intentionally: `./gradlew recordRoborazziDebug`.
- Touching crypto, storage, the manifest or navigation? Also run the instrumented tests:
  `./gradlew connectedDebugAndroidTest` (or `pixel6Api34DebugAndroidTest`). They live in
  `src/androidTest` and run with the Test Orchestrator, so each test starts on cleared app data.
- Explain any new dependency (purpose, licence compatible with GPL-3.0, maintenance).
- Fill in the PR template checklist.

## Local setup

See the README. You can develop the whole UI in demo mode without a Spotify app.

## Licence

By contributing you agree that your contributions are licensed under the GPL-3.0-or-later.
