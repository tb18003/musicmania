# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Use GitHub's
[private vulnerability reporting](../../security/advisories/new) for this repository.
Include steps to reproduce, affected version/commit and impact. You can expect an initial
answer within 7 days. Coordinated disclosure: we aim to ship a fix within 90 days.

## Supported versions

Only the latest release on `master` receives security fixes.

## Threat model (summary)

### Android app

| Asset | Threat | Mitigation |
|---|---|---|
| Spotify credentials | Phishing / credential capture inside the app | Login happens on Spotify's own page in a Custom Tab (RFC 8252); the app never sees the password; no WebView |
| Authorization code | Interception by a malicious app | PKCE (S256, verifier kept in memory) + HTTPS redirect delivered through a **verified** Android App Link; `state` checked against CSRF |
| Access/refresh tokens at rest | Theft from a rooted/stolen device or backups | Encrypted with Tink AES-256-GCM; keyset wrapped by a non-exportable Android Keystore key; token store and keyset excluded from cloud backup and device transfer; associated data binds ciphertext to its purpose |
| Tokens in transit | MITM | HTTPS only (`cleartextTrafficPermitted=false`), system CAs only in release |
| Tokens leaking to third parties | Header sent to image CDN or redirected host | Bearer header is added only for the Spotify API host; images use a separate client; token client does not follow redirects |
| Tokens in logs | Logcat, crash reports | `AuthTokens.toString()` redacted; HTTP logging only in debug, `BASIC` level, `Authorization` redacted; release strips `Log.v/d/i` |
| Refresh token abuse | Revoked/rotated tokens | Rotation honoured; `invalid_grant` wipes the session and asks to sign in again; single-flight refresh avoids races |
| Imported files | Malicious ZIP/JSON (zip bomb, path traversal, huge files) | Storage Access Framework only (no storage permission); entries never extracted to disk; per-entry / total size and entry-count limits; streaming JSON parser; unknown fields ignored |
| Personal data in exports | Over-collection | IP address, country and user-agent fields of the export are never parsed or stored |
| Shared device | Next user sees previous user's data | Sign-out deletes tokens **and** the local database |
| Build secrets | Leaking credentials through the repo | Client ID/redirect from env vars or git-ignored `mobile/local.properties`; no client secret exists (PKCE public client); signing keys via env vars; `gitleaks` in CI |
| Supply chain | Compromised dependency or Gradle distribution | Version catalog, Dependabot, checksum-pinned Gradle wrapper, wrapper validation in CI |
| Tokens sent to the ranking API | Bearer header reaching another host | A separate HTTP client attaches the token only to the configured MusicMania API host (none when `MUSICMANIA_API_URL` is empty) |

### Friends ranking (API + database, optional)

Background: [ADR 0010](docs/adr/0010-supabase-postgres-for-the-ranking.md), [ADR 0011](docs/adr/0011-spotify-token-auth-and-allowlist.md).

| Asset | Threat | Mitigation |
|---|---|---|
| Listening history | Over-sharing | Sharing is opt-in (off by default, never from demo mode); only per-period aggregates and the top 10 (names and public `https` Spotify CDN cover links, validated by both clients and the API) are uploaded, never plays. Users can delete their server data at any time |
| API access | Strangers or forged identities | Every `/v1` route requires a Spotify token verified with Spotify (`GET /v1/me`); identity is the Spotify user ID from that answer, never the request body; manual allowlist (403 `not_allowlisted` otherwise) |
| Tokens at the API | Leaks via logs or cache | Cache keyed by SHA-256 of the token (5 min valid / 30 s rejected); tokens and `Authorization` never logged |
| Any valid Spotify token | `/me` does not reveal the issuing Client ID, so tokens from other apps are accepted | Accepted risk: using it requires that allow-listed user's own token |
| Revoked tokens | Still accepted from cache | At most 5 minutes (cache TTL) |
| API abuse | Floods, oversized or malformed bodies | 120 requests/min per IP, 64 KB body limit, strict validation matching the database checks, RFC 9457 errors |
| Database | Direct access with the public Supabase key | `musicmania` schema not exposed and closed to Supabase API roles; RLS on; API role with least privilege (no DDL); TLS `verify-full`; password in Secret Manager |
| Browser access to the API | Other websites calling it with a user's token | CORS off unless `CORS_ORIGINS` lists the origin (only the web app's) |

### Web app (PWA, optional)

Background: [ADR 0012](docs/adr/0012-web-client-pwa.md).

| Asset | Threat | Mitigation |
|---|---|---|
| Authorization code | Interception, CSRF, leaks via history or referrer | PKCE S256; random `state` validated; single-use login state with a 10-minute TTL; dedicated redirect (`/musicmania/auth/callback`); the code is removed from the address bar before anything runs; `no-referrer` |
| Refresh token at rest | Copying browser storage | AES-256-GCM under a **non-extractable** WebCrypto key in IndexedDB; access token kept in memory only; nothing sensitive in `localStorage` |
| Script injection | XSS reading tokens or data | Strict CSP (`<meta>`, as Pages cannot send headers): first-party scripts only, no inline script or `eval`, network limited to Spotify and the configured API, Spotify image CDNs only; no third-party scripts or analytics; data is always rendered as text (no `{@html}` or `innerHTML`) |
| **Shared origin** | Every GitHub Pages site of the account shares the `<owner>.github.io` origin, hence IndexedDB | Accepted while every site on the origin is ours and static. A script there could use (not extract) the key. Moving to a dedicated domain removes the risk |
| Tokens sent to other hosts | Bearer reaching image CDNs or other origins | Clients refuse any host but `api.spotify.com` and the configured API origin; asserted by unit and end-to-end tests |
| Offline cache | Personal data persisted by the service worker | The service worker precaches the static shell only, never Spotify or API responses |
| Imported files | Zip bombs, path traversal, huge files | Processed in a Web Worker, streamed; only history files inflated; 64 MiB per file, 4 GiB total, 2,000 entries; nothing written outside IndexedDB; IP/country/user-agent fields never read |
| Shared device | Next user sees previous user's data | Sign-out deletes the whole database (tokens, key, history) |
| Storage eviction | iOS clearing website data | Persistent storage requested; JSON export in Settings |

### Decisions

- **No certificate pinning.** Spotify and Google Cloud rotate certificates without notice; a stale
  pin would break the app for every user, and pinning offers little extra protection over the
  platform trust store for this threat model.
- **No client secret.** A secret embedded in an APK or a web page is not secret. Both clients are
  PKCE public clients. The optional ranking API needs no secret in the clients either (ADR 0011).
- **Local first.** Without the optional ranking, nothing leaves the device; with it, only opt-in
  aggregates do.
- **Tink instead of EncryptedSharedPreferences**, which is deprecated.
- **Release builds** use R8 full mode with resource shrinking.

## Hardening checklist for contributors

- Never log tokens, auth codes or headers; never include them in exceptions or analytics.
- Do not add permissions without discussion. Do not add `WebView`-based login.
- Keep `exported=false` for components unless strictly required.
- Validate and bound any new input that comes from files, intents or the network.
- Web: no inline scripts, `eval`, `{@html}` or `innerHTML` with data; keep the CSP in `web/csp.config.ts` strict and
  tested; never store tokens in `localStorage`; new hosts need a CSP change and a reason.
