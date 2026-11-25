# ADR 0011 — The API authenticates with the user's Spotify token, plus a manual allowlist

- Status: Accepted · Date: 2026-11-24

## Context
The ranking API must refuse anyone who is not signed in and allow-listed. The apps already hold
a Spotify access token, and a second login (accounts, passwords, e-mail links) would be friction
and more secrets to protect. Since 2026 Spotify does not return the user's e-mail to Development
Mode apps, so e-mail cannot be the identity.

## Decision
- **Credential: the Spotify access token**, sent as a bearer token. The apps reuse their existing
  token and refresh flow; there is no MusicMania account or password.
- **Verification:** the API asks Spotify who the token belongs to (`GET /v1/me`, 3 s timeout).
  - A rejected token gets 401 `invalid_token`. The apps then refresh and retry once.
  - When Spotify is unavailable or rate-limited, the API answers 503 with `Retry-After`, never 401.
- **Cache:** results are cached by **SHA-256 of the token**, never the token itself.
  - A valid token is cached for 5 minutes, a rejected one for 30 seconds.
  - This keeps Spotify calls well under the quota shared by the developer account.
- **Authorization:**
  - **Identity** is the Spotify user ID from `/me`, never anything in the request body; each user can only write their own data.
  - **Access** comes from the `allowlist` table (Spotify user ID, with the e-mail as a label for the admin), managed by hand.
  - **Failures:** an account that is unknown or disabled gets 403 `not_allowlisted`. The apps then show the user's Spotify ID so they can send it to the admin.
- **Other protections:**
  - every `/v1` route is authenticated (only `/health` is public);
  - rate limit of 120 requests/min per IP;
  - 64 KB body limit;
  - RFC 9457 problem responses;
  - tokens and the `Authorization` header are never logged.
- **Browsers** (the web client, [ADR 0012](0012-web-client-pwa.md)): CORS is off by default and
  enabled only for origins listed in `CORS_ORIGINS`.

## Consequences
+ No new login, no password database, no secret in the apps (both stay PKCE public clients).
+ Revoking someone is one SQL `UPDATE`; Spotify-side revocation also locks them out within 5 minutes.
− `/me` does not say which Client ID issued a token, so any valid token of an allow-listed user
  is accepted. Abusing that requires that user's own token, so the risk is accepted and recorded in
  `SECURITY.md`.
− A token stays usable against the API for up to 5 minutes after Spotify revokes it (cache TTL).
