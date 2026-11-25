# ADR 0012 — Web client as an installable PWA (TypeScript + Svelte, GitHub Pages)

- Status: Accepted · Date: 2026-11-25

## Context
Friends without Android want to use MusicMania and join the ranking. Shipping an iOS app
outside the App Store needs the Apple Developer Program (yearly fee), a Mac or macOS CI runner,
TestFlight (builds expire after 90 days) or 7-day sideloading. That is a lot of cost and friction
for a handful of users. iOS supports **home screen web apps**: opened in Safari, added with
"Share → Add to Home Screen", they run full screen and keep their own storage.

## Decision
- **A progressive web app in `web/`**, written in **TypeScript + Svelte 5 + Vite**. It is a
  separate client of the same Spotify Web API and MusicMania API, not a port of the Android code.
  Kotlin Multiplatform with Compose for Web was rejected: its web target is still Beta, needs
  Safari 18.2+, draws on a canvas (weaker accessibility, heavier download) and would force the
  Android modules off `java.time`.
- **Same architecture, enforced the same way.** `src/lib/{model,domain,data,ui}`, `src/features`
  and `src/app` mirror the Android modules. ESLint `no-restricted-imports` rules play the part of
  the Konsist tests (features never import data; the domain imports no storage, network or UI).
  Domain logic (dedupe key, 30-second stream rule, streaks, Borda derivations, ranking periods,
  shared aggregates) is ported with the same test cases as Kotlin, so both clients agree.
- **API contract.** The cloud client's types are generated from `api/openapi.yaml`, and CI fails
  if they are stale.
- **Hosting on GitHub Pages** (project site, `https://<owner>.github.io/musicmania/`), deployed by
  `.github/workflows/deploy-web.yml` on `web-v*` tags. Hash routing avoids server configuration.
- **Android sign-in keeps working.** The project site takes over `/musicmania/*`, which is where
  the Android App Link redirect (`/musicmania/callback`) lives. The PWA therefore ships the same
  static, script-free fallback page (`web/public/callback.html`), and `/.well-known/assetlinks.json`
  stays on the user site at the root. The web client signs in through its own redirect,
  `/musicmania/auth/callback`, so the app never intercepts a browser login.
- **No background work.** iOS gives web apps no background sync. The PWA syncs
  `recently-played` when opened and when it returns to the foreground (throttled), and lifetime
  history comes from importing the Spotify data export on the device. No server-side sync: the
  server would have to hold refresh tokens and raw history, against security rules 4 and 10.

## Security
- PKCE public client, same Client ID as the app. Access token in memory only. The refresh token
  is encrypted with AES-GCM under a **non-extractable** WebCrypto key and stored in IndexedDB.
- Strict CSP as a `<meta>` tag (Pages cannot send headers): first-party scripts only, no eval,
  network limited to Spotify and the configured API, Spotify image CDNs only. The service worker
  caches the static shell only, never Spotify or API responses.
- **Shared origin.** `<owner>.github.io` is one origin for every Pages site of the account, so
  they share storage. A script running on another site of that origin could read MusicMania's
  IndexedDB and use the key (it cannot extract the raw key). Accepted for now because every site
  on the origin is ours and static; moving to a dedicated domain removes the risk.

## Consequences
+ Zero cost, no store review, one URL to share; works on iPhone, iPad, Android and desktop.
+ Same privacy model as the app: everything computed on the device, ranking opt-in, aggregates only.
− History has gaps if the user does not open the app for more than ~50 plays; the import fills them.
− A second client to maintain. Shared logic is kept small and pinned by parity tests.
− Safari may evict storage of web apps that are not installed; the app asks for persistent
  storage and offers a JSON export.
