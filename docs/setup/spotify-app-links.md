# Spotify login setup (HTTPS redirect + Android App Links)

Since 2025 Spotify only accepts **HTTPS** redirect URIs (or loopback IPs, unusable on a
phone); custom schemes such as `musicmania://callback` are rejected. MusicMania therefore
receives the OAuth redirect through a **verified Android App Link**: Android hands the HTTPS
URL straight to the app instead of the browser, and only after verifying that the domain
trusts the app's signing certificate.

## 1. Pick a redirect URL on a domain you control

The file `/.well-known/assetlinks.json` must be served from the **root** of the host:

| Option | Redirect example | Where assetlinks.json lives |
|---|---|---|
| GitHub user/org site (repo `<you>.github.io`) | `https://<you>.github.io/musicmania/callback` | `https://<you>.github.io/.well-known/assetlinks.json` |
| Your own domain | `https://music.example.com/callback` | `https://music.example.com/.well-known/assetlinks.json` |

A *project* GitHub Pages site (`<you>.github.io/musicmania`) cannot serve the root file by
itself — use the user site repository for the `.well-known` folder.
Do not enable GitHub Pages on the `musicmania` repository itself while the callback lives at
`<you>.github.io/musicmania/…`: a project site takes over that path and would shadow the callback page.

## 2. Get your signing certificate fingerprint(s)

```bash
./gradlew :app:signingReport
```
Copy the `SHA-256` of the **debug** variant (for development) and of your **release** key.

## 3. Publish `assetlinks.json`

Start from [`docs/app-links/assetlinks.json`](../app-links/assetlinks.json), replace the
fingerprints and publish it at `https://<host>/.well-known/assetlinks.json`
(content type `application/json`, served over HTTPS, no redirects).

For GitHub Pages also add an empty `.nojekyll` file at the repository root, otherwise
folders starting with a dot are not published.

Optionally publish [`docs/app-links/callback/index.html`](../app-links/callback/index.html) at
the redirect path: if verification ever fails, the browser shows a friendly page instead of a 404.

## 4. Register the app with Spotify

In the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard):
1. Create an app (the owner needs an active Premium subscription), API: *Web API*.
2. Add your redirect URL exactly as chosen in step 1.
3. Under *User Management* add the Spotify accounts that may use the app (max. 5 in Development Mode).
4. Copy the **Client ID** (never the client secret — it is not used).

## 5. Configure the build

`local.properties` (git-ignored) or environment variables in CI:
```properties
SPOTIFY_CLIENT_ID=0123456789abcdef0123456789abcdef
SPOTIFY_REDIRECT_URI=https://<you>.github.io/musicmania/callback
```
The build derives the App Link intent filter (`host`/`path`) from `SPOTIFY_REDIRECT_URI`.

## 6. Verify

Install the app, then:
```bash
adb shell pm verify-app-links --re-verify io.github.tb18003.musicmania
adb shell pm get-app-links io.github.tb18003.musicmania
```
Your host must show `verified`. Troubleshooting: check the fingerprint matches the installed
build, that the JSON is reachable without redirects, and that the package name is correct.
