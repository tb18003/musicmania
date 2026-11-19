# ADR 0002 — OAuth Authorization Code + PKCE with AppAuth and HTTPS App Links

- Status: Accepted · Date: 2026-11-19

## Context
Spotify removed the implicit grant and HTTP/custom-scheme redirects (Nov 2025). Mobile apps
cannot keep a client secret. Users must never type their password inside our UI.

## Decision
- Authorization Code flow with **PKCE (S256)**, public client, no secret.
- **AppAuth-Android** handles request building, Custom Tabs, `state` validation and code exchange
  (RFC 8252 best practices) instead of a hand-rolled implementation.
- The redirect is an **HTTPS verified App Link**; AppAuth's custom-scheme receiver activity is
  replaced in the manifest. Host/path come from `SPOTIFY_REDIRECT_URI` at build time.
- Token refresh is done by our own `TokenRefreshCoordinator` inside OkHttp (proactive before
  expiry + reactive on 401), single-flight to avoid refresh storms, honouring token rotation.

## Consequences
+ Industry-standard, audited flow; credentials never touch the app.
− Each developer/forker must host `assetlinks.json` on a domain they control (documented).
