# ADR 0007 — Baseline Profile and startup benchmarks

- Status: Accepted · Date: 2026-11-19

## Context
A Compose app runs interpreted/JIT code on first launches until ART compiles it in the
background. Cold start and first navigations are what users notice most, and MusicMania is
distributed outside Play too (GitHub releases), where Cloud Profiles do not exist.

## Decision
- A `:baselineprofile` module (`com.android.test` + `androidx.baselineprofile`) generates the
  profile from the critical journeys: cold start, onboarding, **demo sign-in** (no Spotify
  credentials needed) and every top-level screen with scrolling.
- The generated `baseline-prof.txt` and `startup-prof.txt` are **committed** under
  `app/src/release/generated/baselineProfiles/` (`automaticGenerationDuringBuild = false`), so
  release builds are reproducible and need no device. `dexLayoutOptimization` uses the startup
  profile to place startup classes in the primary DEX.
- `androidx.profileinstaller` installs the profile on devices that don't get it from Play.
- `StartupBenchmark` (Macrobenchmark) compares cold start with no compilation vs. the profile.
- UI automation uses test tags exposed with `testTagsAsResourceId` (demo button, bottom bar),
  so journeys do not depend on the device language.

## Security notes
The plugin adds `nonMinifiedRelease` and `benchmarkRelease` build types. Only these are
`profileable` (shell), and without a release keystore they are signed with the **debug key**:
they are local measurement artifacts and must never be distributed. `release` is neither
profileable nor debuggable (verified in the packaged manifest).

## Consequences
+ Faster cold start and first frames: on an API 36 emulator the median time to initial display
  went from 684 ms to 600 ms (−12 %); physical devices usually gain more.
− The profile must be regenerated after large UI changes:
  `./gradlew :app:generateReleaseBaselineProfile` (managed device `pixel6Api34Aosp`), or
  `-Pmusicmania.baselineprofile.connected` with a running API 33+ emulator/phone.
