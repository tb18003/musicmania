# ADR 0013 — One release version for the repository, and an update check for Android

- Status: Accepted · Date: 2026-11-27

## Context
The Android app is installed outside any store, so nothing tells its users that a new build exists.
Each part also carried its own version: the app said `0.1.0`, `web/package.json` said `0.1.0` while
the web was released as `web-v0.3.0`, and the API had a third number. Users and maintainers could not
tell which build matched which release.

## Decision
- **`web/package.json` holds the single release version** (MAJOR.MINOR.PATCH) for the whole repository:
  - the web app shows it in Settings, as before;
  - Android reads it at build time: `versionName` is that value, and `versionCode` is
    `MAJOR * 10000 + MINOR * 100 + PATCH` (MINOR and PATCH stay below 100), so it always grows;
  - the API embeds it in the jar (`generateBuildInfo`). The Docker build only sees `api/`, so the
    workflows pass it with `--build-arg APP_VERSION`.
- **`GET /v1/app/version`** returns that version. It is public: it identifies nobody, and the app may be
  signed out or in demo mode. Every other `/v1` route still requires the Spotify token; a transparent
  child route scopes the authentication plugin so the public route can live next to them.
- **Android checks once per app start** (only when `MUSICMANIA_API_URL` is configured). When the API
  announces a newer version, a dialog offers to open the GitHub Releases page. Any failure, or a
  malformed version, shows nothing: the check never gets in the way. Settings shows the installed
  version next to the open-source libraries.

## Consequences
- **Release notes** live in `api/release-notes.json`: a few short lines per release, in English and
  Spanish.
  - The API embeds the file and returns the notes of its release and the previous ones.
  - The update dialog lists those of the releases the user is missing.
  - `release-android.yml` opens the GitHub release with them.
  - A test fails when the current version has no notes.
- Releasing means: bump `web/package.json` (and its lockfile) and add its notes, then tag `web-v*`. That tag deploys the
  web and builds the signed APK into the GitHub release (`release-android.yml`, see
  `docs/setup/android-release.md`). Then tag `api-v*`: the deployed API is what announces a release, so
  it goes last.
- The deploy workflow's smoke test checks that the API announces the version in `web/package.json`.
- A store-distributed app would use the store's update mechanism instead; this endpoint would remain
  useful for sideloaded builds.
