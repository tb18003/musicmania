# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Use GitHub's
[private vulnerability reporting](../../security/advisories/new) for this repository.
Include steps to reproduce, affected version/commit and impact. You can expect an initial
answer within 7 days. Coordinated disclosure: we aim to ship a fix within 90 days.

## Supported versions

Only the latest release on `master` receives security fixes.

## Threat model (summary)

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

### Decisions

- **No certificate pinning.** Spotify rotates certificates without notice; a stale pin would
  break the app for every user, and pinning offers little extra protection over the platform
  trust store for this threat model. Revisit if the app ever talks to a self-operated backend.
- **No client secret / no backend.** A secret embedded in an APK is not secret. PKCE removes the
  need for it.
- **Tink instead of EncryptedSharedPreferences**, which is deprecated.
- **Release builds** use R8 full mode with resource shrinking.

## Hardening checklist for contributors

- Never log tokens, auth codes or headers; never include them in exceptions or analytics.
- Do not add permissions without discussion. Do not add `WebView`-based login.
- Keep `exported=false` for components unless strictly required.
- Validate and bound any new input that comes from files, intents or the network.
