# CLAUDE.md — MusicMania

Guidance for AI coding agents (and humans) working in this repository. Read it fully
before changing code. It is the single source of truth for architecture, conventions,
security rules and workflow.

---

## 1. What this project is

**MusicMania** is an open-source (GPL-3.0-or-later) native Android app, written in Kotlin
with Jetpack Compose, that shows rich statistics about a user's Spotify listening, in the
spirit of stats.fm: top tracks/artists/albums/genres with ranking movement, listening
history, a 24 h listening clock, heatmaps, streaks, milestones, a story-style "Recap"
(Wrapped any time), a home-screen widget, and **free on-device import of Spotify's
Extended Streaming History** for lifetime stats.

Principles: **privacy first** (everything is computed and stored on the device, there is
no backend), **offline first** (the UI always reads the local database), **secure by
default**, and **delightful motion** that respects accessibility.

- Repository: `tb18003/musicmania` · App name: `MusicMania` · Package: `io.github.tb18003.musicmania`
- Code, comments, commits and docs: **English**. UI strings: **English + Spanish** (`values/`, `values-es/`).

## 2. Spotify Web API constraints (verified November 2026) — read before adding features

These constraints shape the product. Do not design features that depend on removed APIs.

- **Development Mode** (since Feb/Mar 2026): app owner needs Premium; **max 5 allow-listed
  users** (others get HTTP 403 → `AppError.Forbidden`). Extended quota is only for large
  businesses, so MusicMania is "bring your own Client ID".
- **Removed for Dev Mode apps**: batch endpoints (`GET /tracks?ids=`, `/artists?ids=`,
  `/albums?ids=`), `artists/{id}/top-tracks`, `browse/new-releases`, `users/{id}`, and fields
  `popularity`, `followers`, `label`, `email`, `country`, `product`. `audio-features` and
  `recommendations` are deprecated since Nov 2024. ⇒ Fetch items individually and cache them.
- **Available**: `/me`, `/me/top/{tracks|artists}` (`short_term`/`medium_term`/`long_term`),
  `/me/player/recently-played` (**last 50 plays only**), `/me/player/currently-playing`,
  `/me/tracks`, `/me/playlists`, `POST /me/playlists`, `POST /playlists/{id}/items`,
  `/tracks/{id}`, `/artists/{id}` (has `genres`), `/albums/{id}`, search (limit ≤ 10).
- **Redirect URIs must be HTTPS** (custom schemes are rejected). We use a verified Android
  App Link (see `docs/setup/spotify-app-links.md`).
- **429 `QUOTA_EXCEEDED`**: quota is shared by all Client IDs of a developer account.
  Poll sparingly, cache aggressively, honour `Retry-After`.

Consequences baked into the design:
1. `recently-played` only returns 50 plays ⇒ a **WorkManager job syncs every ~60 min** and
   accumulates history locally.
2. Lifetime stats come from **importing the user's data export** (ZIP/JSON), parsed on device.
3. "Top albums/genres" are **derived** (Borda count) from top tracks/artists.
4. Ranking movement (▲▼ NEW) comes from **daily snapshots** stored in Room.

## 3. Architecture

Clean Architecture + MVVM with **Unidirectional Data Flow**, multi-module like *Now in Android*.

```
app ─────────────► feature:* ─────► core:domain ◄──── core:data ───► core:network
 │                    │                 ▲                 │      └──► core:database
 │                    ├──► core:ui ─────┤                 └─────────► core:datastore
 │                    └──► core:designsystem              core:auth ─► core:datastore
 ├──► sync:work ─────────► core:domain
 └──► widget ────────────► core:domain
core:model / core:common: pure Kotlin, used by everyone. core:testing: fakes for tests.
```

| Module | Type | Responsibility |
|---|---|---|
| `app` | Android app | `MainActivity`, Application (Hilt, WorkManager, Coil), NavHost wiring, bottom bar, `SpotifyConfig` from BuildConfig |
| `core:model` | JVM | Immutable domain models (`Track`, `Ranked`, `ListeningSummary`, `Recap`, …) |
| `core:common` | JVM | `DataResult`/`AppError`, dispatcher qualifiers, `Clock`, `SpotifyConfig` |
| `core:domain` | JVM | **Repository interfaces**, pure stat calculators, use cases |
| `core:data` | Android lib | Repository implementations, mappers, importer, demo seeder |
| `core:network` | Android lib | Retrofit API, DTOs, auth interceptor/authenticator, rate limiting |
| `core:database` | Android lib | Room entities, DAOs (SQL aggregations), exported schemas |
| `core:datastore` | Android lib | Tink-encrypted token store, preferences, sync state |
| `core:auth` | Android lib | OAuth Authorization Code + PKCE via AppAuth |
| `core:designsystem` | Compose lib | Theme, typography, motion tokens, components, Canvas charts |
| `core:ui` | Compose lib | Shared feature UI (`MediaRow`, selectors, states), formatters, shared-element helpers, common strings |
| `core:testing` | JVM | In-memory fakes of every repository, `MainDispatcherRule`, `TestData` |
| `sync:work` | Android lib | `SyncWorker`, `WorkManagerSyncScheduler`, `SyncObserver` multibinding |
| `widget` | Compose lib | Glance home-screen widget |
| `feature:*` | Compose lib | `auth`, `home`, `top`, `history`, `detail`, `insights`, `recap`, `importer`, `settings` |

### Dependency rules (enforced by Konsist tests in `app/src/test`)
- `feature:*` → only `core:domain`, `core:ui`, `core:designsystem`, `core:model`, `core:common`.
  **Never** another feature, never `core:data|network|database|datastore`.
  Sole documented exception: `feature:auth` → `core:auth` (it launches the OAuth intent).
- `core:domain` is pure Kotlin (only `androidx.paging:paging-common` allowed).
- Repository **interfaces live in `core:domain`**; implementations are `internal` in `core:data`
  and bound with Hilt `@Binds` (dependency inversion).
- Cross-feature navigation is wired **only in `app`** (`MusicManiaNavHost`) via callbacks.

### Patterns in use
Repository · Use Case/Interactor · Strategy (API ranges vs. local history in `GetTop*UseCase`)
· Adapter/Mapper (DTO ↔ entity ↔ domain) · Observer (Flows; `SyncObserver` multibinding)
· Single-flight lock (token refresh) · Offline-first single source of truth (Room)
· Sealed result/error types · DI with Hilt.

### Data flow
UI (Compose) → events → `ViewModel` → use case → repository interface → (Room ⇄ network).
State flows back as `StateFlow<UiState>` collected with `collectAsStateWithLifecycle()`.
One-shot events (snackbars) use a `Channel` exposed as `Flow` **with a single collector**.

## 4. Tech stack

Kotlin 2.4.21 · AGP 9.5.1 (built-in Kotlin, new DSL) · Gradle 9.9.1 (checksum-pinned wrapper) · JDK 21 to run Gradle,
bytecode target 17 · compileSdk/targetSdk 36 · minSdk 26 · Compose BOM 2025.12.00 + Material 3
· Navigation Compose (type-safe `@Serializable` routes) · Hilt 2.60 + KSP 2.3 · Coroutines/Flow
· Retrofit 3 + OkHttp 4 + kotlinx.serialization · Room 2.8 + Paging 3 · DataStore · Tink ·
AppAuth · Coil 3 · Palette · WorkManager · Glance · SplashScreen.
All versions live in `gradle/libs.versions.toml`. Shared build logic lives in
`build-logic/convention` (convention plugins `musicmania.*`).

> AGP 9 uses built-in Kotlin: never apply `org.jetbrains.kotlin.android` and never add the
> `android.builtInKotlin`/`android.newDsl` opt-out flags (see ADR 0006).
> Known follow-up: Detekt 1.23 uses Gradle APIs removed in Gradle 10 ⇒ migrate to Detekt 2 in its own PR.

## 5. Commands

Use JDK 21 (Android Studio's JBR works): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.

```bash
./gradlew assembleDebug                 # build the app
./gradlew testDebugUnitTest test        # all JVM unit tests (Android + pure Kotlin modules)
./gradlew :feature:top:testDebugUnitTest  # one module
./gradlew spotlessApply                 # format + add GPL license headers (run before committing)
./gradlew spotlessCheck detekt          # static analysis (CI gate)
./gradlew lintDebug                     # Android Lint
./gradlew koverHtmlReport               # aggregated coverage → build/reports/kover/html
./gradlew recordRoborazziDebug          # (re)record screenshot baselines
./gradlew verifyRoborazziDebug          # compare screenshots against baselines
```

Definition of "green" before any commit: `spotlessCheck detekt testDebugUnitTest test assembleDebug`.

## 6. Conventions

**Kotlin / general**
- Immutability first: `val`, data classes, `List` (not `MutableList`) in public APIs.
- No `!!` in production code. No `GlobalScope`. Inject dispatchers (`@Dispatcher(IO)`) and `Clock`.
- Errors: return `DataResult<T>` with a typed `AppError`; never leak exceptions across layers.
  Rethrow `CancellationException`.
- Magic numbers become named constants (Detekt enforces it outside `designsystem`/tests).
- Every source file starts with the GPL header — `spotlessApply` adds it automatically.
- Max line length 140; formatting is ktlint (android_studio style) via Spotless.

**Naming**
- `XxxUseCase` (single `operator fun invoke`), `XxxRepository` (interface) / `DefaultXxxRepository`,
  `OfflineFirstXxxRepository`, `CachedXxxRepository` (impls), `XxxViewModel`, `XxxUiState`,
  `XxxScreen` (stateless) + `XxxRoute` (stateful, gets the ViewModel), `XxxDestination` (nav route).
- Resources are prefixed with the module path: `feature_top_*`, `core_ui_*` (enforced by `resourcePrefix`).

**Compose**
- Stateless screens receive state + lambdas; previews use the stateless version (`@Preview` +
  `MusicManiaTheme`).
- Use design-system tokens only: `MaterialTheme.colorScheme`, `MusicManiaTheme.extendedColors`,
  `Motion`, `motionTween()/motionSpring()`. No hard-coded colours in features (charts/brand cards excepted).
- **Every animation must respect `LocalReduceMotion`** (use the provided helpers, they already do).
- Shared elements: `Modifier.sharedElementOrSelf(SharedKeys.artwork(kind, name, artist))`;
  destinations call `ProvideNavAnimatedScope { … }`.
- Charts provide a `description` for TalkBack. Touch targets ≥ 48 dp. Text from `stringResource`.

**ViewModels**
- `@HiltViewModel`, expose `StateFlow<UiState>` via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- Persist user selections that matter across process death in `SavedStateHandle`.
- No Android `Context` in ViewModels (except via injected abstractions).

## 7. Security rules (non-negotiable)

1. **Never log, print, toast, or put in exceptions any token, auth code or Authorization header.**
   `AuthTokens.toString()` is redacted; HTTP logging is debug-only, BASIC level, header redacted.
2. **No secrets in the repo.** `SPOTIFY_CLIENT_ID`/`SPOTIFY_REDIRECT_URI` come from env vars or
   git-ignored `local.properties` (see `local.properties.example`). Keystores never committed.
   There is no client secret anywhere (PKCE public client) — do not add one.
3. OAuth = Authorization Code **+ PKCE** via AppAuth in a **Custom Tab** (never a WebView),
   `state` validated, HTTPS **verified App Link** redirect.
4. Tokens are stored **only** through `TokenStore` (Tink AES-256-GCM, keyset wrapped by an
   Android Keystore key). Do not use `EncryptedSharedPreferences` (deprecated) or plain prefs.
5. The bearer token is attached **only** to the Spotify API host (`AllowedApiHost`); images use
   `@PublicHttpClient`. Token refresh goes through `TokenRefreshCoordinator` (single flight).
6. Backups exclude the token store and the Tink keyset (`data_extraction_rules.xml`, `backup_rules.xml`).
   If you rename those files, update the rules.
7. Network: HTTPS only (`network_security_config.xml`). No certificate pinning (documented decision).
8. Treat imported files as **untrusted input**: SAF only, streamed parsing, size/entry limits
   (`ImportLimits`), never extract to disk, never store IP/location fields from the export.
9. Sign-out wipes tokens **and** local data. Keep it that way.
10. New dependencies require justification in the PR (licence must be GPL-3.0 compatible,
    actively maintained). Prefer the platform/AndroidX.

## 8. Testing strategy

| Level | Where | Tools |
|---|---|---|
| Pure logic (calculators, use cases) | `core:domain` | JUnit4, kotlin.test, coroutines-test, Turbine, `core:testing` fakes |
| ViewModels | each `feature:*` | `MainDispatcherRule`, fakes (`InMemoryHistoryRepository` is behavioural, not a mock) |
| Network | `core:network` | MockWebServer (auth, refresh concurrency, 401/403/429, parsing fixtures) |
| Database | `core:database`, `core:data` | Robolectric + in-memory Room |
| Import/security | `core:data` | Real ZIP/JSON fixtures, zip-bomb and invalid-input tests |
| Workers | `sync:work` | WorkManager testing + Robolectric |
| Architecture | `app/src/test` | Konsist (layer rules, naming) |
| Screenshots | `core:designsystem` | Roborazzi (Robolectric native graphics) |

Rules: write tests **with** the change, not after. Prefer fakes over mocks. Construct
ViewModels **inside** the test (after `MainDispatcherRule` installs Main). Use `TestData.fixedClock`.
Bug fix ⇒ first a failing test that reproduces it.

## 9. Adding a feature (recipe)

1. If new data is needed: model in `core:model` → repository interface/method in `core:domain`
   → implementation + mapper in `core:data` (+ DAO/DTO) → fake in `core:testing`.
2. Business logic in a use case in `core:domain/usecase` with unit tests.
3. Create `feature/<name>` with `plugins { alias(libs.plugins.musicmania.android.feature) }` and
   add it to `settings.gradle.kts` and `app/build.gradle.kts`.
4. `XxxViewModel` + `XxxUiState` + stateless `XxxScreen` + `XxxRoute` + `navigation/XxxNavigation.kt`
   (`@Serializable` destination + `NavGraphBuilder.xxxScreen(callbacks)`).
5. Strings in `values/` **and** `values-es/` with the module prefix.
6. Wire navigation in `app/.../MusicManiaNavHost.kt`.
7. Tests (ViewModel at minimum), `spotlessApply`, green checks, update docs if behaviour changes.

## 10. Git workflow

- `master`: stable releases only (tagged `vX.Y.Z`). `develop`: integration branch.
- Branch from `develop`: `feature/<short-name>`, `fix/<short-name>`, `chore/…`, `docs/…`;
  release branches `release/x.y.z` → PR to `master` (+ back-merge to `develop`); urgent
  `hotfix/x.y.z` from `master`.
- Everything reaches `develop`/`master` through a **PR** with green CI. No direct pushes.
- **Conventional Commits**: `feat(top): add genre bars`, `fix(network): …`, `test:`, `docs:`,
  `refactor:`, `build:`, `ci:`, `chore:`. Small, atomic, each commit builds.
- Version: `versionName` follows SemVer; bump `versionCode` on every release.

## 11. Working agreement for AI agents ("vibe coding" with guardrails)

- **Read before you write**: open the files you will touch and their tests; follow existing patterns.
- **Small, verifiable steps**: one concern per change; run the module's tests after each step;
  run the full green check before committing.
- **Plan first** for anything spanning several modules; state assumptions explicitly.
- **Don't invent APIs**: check §2 before using a Spotify endpoint; check the version catalog
  before using a library API; compile to verify.
- **Never weaken security or tests to make something pass** (no disabling Detekt rules globally,
  no `@Ignore`, no catching `Throwable`, no `!!`). If a rule is wrong, discuss it in the PR.
- **No new dependencies or modules** without a reason in the PR description.
- Keep UI strings translatable (EN + ES), accessibility labels on icons that act as buttons.
- Update `README.md`/ADRs (`docs/adr/`) when you change architecture or user-visible behaviour.
- Leave the codebase cleaner: delete dead code, but don't do drive-by refactors in feature PRs.

## 12. Where things are

- Build logic: `build-logic/convention/src/main/kotlin/`
- Version catalog: `gradle/libs.versions.toml` · Detekt: `config/detekt/detekt.yml` · ktlint: `.editorconfig`
- Room schemas (commit them!): `core/database/schemas/`
- Architecture decisions: `docs/adr/` · App Links / OAuth setup: `docs/setup/spotify-app-links.md`
- Security policy and threat model: `SECURITY.md` · Contribution guide: `CONTRIBUTING.md`
