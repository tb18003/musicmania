# ADR 0006 — AGP 9 with built-in Kotlin and the new DSL

- Status: Accepted · Date: 2026-11-19
- Supersedes the toolchain versions listed in `CLAUDE.md` §4 (kept unchanged on purpose).

## Context
The project started on AGP 8.13 / Gradle 8.13 / Kotlin 2.2 for stability. AGP 9 makes built-in
Kotlin and the new DSL the default (they become mandatory in AGP 10), and Dependabot already
proposed AGP 9.5.1 and Gradle 9.9.1.

## Decision
Upgrade the toolchain as one unit: **AGP 9.5.1, Gradle 9.9.1, Kotlin 2.4.21, KSP 2.3.14,
Hilt 2.60.1, Kover 0.9.11, Roborazzi 1.79.0** (AGP 9.5 needs Gradle ≥ 9.6; AGP 9 needs
KSP ≥ 2.3.6 and Hilt ≥ 2.59.2). No opt-out flags (`android.builtInKotlin`, `android.newDsl`).

- `org.jetbrains.kotlin.android` is removed everywhere; AGP compiles Kotlin itself. The
  Compose compiler and kotlinx.serialization plugins are still applied explicitly.
- Convention plugins use the non-generic `CommonExtension` and property access
  (`defaultConfig.apply { … }`); the block functions such as `defaultConfig { }` no longer exist on it.
- Android test modules depend on `kotlin-test-junit` explicitly: built-in Kotlin does not infer
  the JUnit variant of `kotlin-test`, so `kotlin.test.Test` would not resolve.
- Unit-test tasks are created only for modules that have `src/test`: Gradle 9 fails a test task
  that discovers no tests.
- `consumer-rules.pro` is declared only when the file exists (AGP 9 fails on missing files).
- ktlint's `class-signature` rule now runs under the android_studio style, so the threshold that
  `.editorconfig` already declared (multiline at ≥ 2 parameters) is enforced; sources were
  reformatted in a dedicated `style:` commit.

## Consequences
+ Simpler builds, one fewer plugin to keep in lock-step with AGP, ready for AGP 10.
+ Kotlin 2.4 compiler and current Hilt/KSP.
− Detekt 1.23.8 triggers Gradle deprecations (`ReportingExtension.file`, `Configuration.setVisible`)
  removed in Gradle 10/11 ⇒ follow-up: migrate to Detekt 2 (`dev.detekt`) in its own PR.
