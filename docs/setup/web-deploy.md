# Deploying the web app (GitHub Pages)

The web client (`web/`) is published as the repository's GitHub Pages **project site**:
`https://<owner>.github.io/musicmania/`. Allow ~15 minutes the first time.
Background: [ADR 0012](../adr/0012-web-client-pwa.md).

```
Safari / any browser ──PKCE──► accounts.spotify.com
        │  └─ Spotify token ─► api.spotify.com (stats) and the MusicMania API (ranking, CORS)
        └─ static files from GitHub Pages (also serves the Android App Link fallback page)
```

## 1. Spotify Developer Dashboard

Open your app → **Settings** → **Redirect URIs** and add (keep the Android one):

```
https://<owner>.github.io/musicmania/auth/callback
```

For local development you can also add `http://127.0.0.1:5173/musicmania/auth/callback`
(Spotify accepts loopback HTTP). In **User Management**, add every person who will use the app
(Development Mode allows 5 users per app, Android and web together).

## 2. Repository variables

GitHub → **Settings** → **Secrets and variables** → **Actions** → **Variables** tab. These values
are public (the web app is a PKCE public client and they end up in the page), so they are
variables, not secrets:

| Variable | Value |
|---|---|
| `SPOTIFY_CLIENT_ID` | The same Client ID as the Android app |
| `SPOTIFY_REDIRECT_URI_WEB` | `https://<owner>.github.io/musicmania/auth/callback` |
| `MUSICMANIA_API_URL` | Your Cloud Run URL (`https://…run.app`), or leave unset to hide the ranking |

## 3. Turn on GitHub Pages

GitHub → **Settings** → **Pages** → **Build and deployment** → Source: **GitHub Actions**.

Then **Settings** → **Environments** → `github-pages` → **Deployment branches and tags**: add a
**tag** rule `web-v*` (by default only the default branch may deploy, and deployments run from tags).

> The project site takes over every `/musicmania/…` path of `<owner>.github.io`, including the
> Android redirect `/musicmania/callback`. The web build ships the same fallback page, and
> `/.well-known/assetlinks.json` stays on the user site, so Android sign-in is unaffected.

## 4. Allow the web origin on the API

The browser calls the API from `https://<owner>.github.io`, so the API must allow that origin
(CORS is off until `CORS_ORIGINS` is set). In Cloud Shell:

```bash
gcloud run services update musicmania-api --region=europe-southwest1 \
  --update-env-vars=CORS_ORIGINS=https://<owner>.github.io
```

## 5. Deploy

From `develop` (or `master` once released), tag and push:

```bash
git tag web-v0.1.0
git push origin web-v0.1.0
```

Or run **Actions → Deploy web → Run workflow**. The job runs the unit tests, builds, publishes
and checks that both the app and `callback.html` are online.

## 6. Check

- `https://<owner>.github.io/musicmania/` opens MusicMania.
- `https://<owner>.github.io/musicmania/callback` shows the "Almost there" page.
- Sign in from the Android app: it must still return to the app.

## Operations

| Task | How |
|---|---|
| New release | Bump `version` in `web/package.json`, merge, tag `web-vX.Y.Z` |
| Roll back | Re-run **Deploy web** on the previous tag (Actions → the old run → Re-run) |
| Change the API URL | Update the `MUSICMANIA_API_URL` variable and redeploy (it is also part of the CSP) |
