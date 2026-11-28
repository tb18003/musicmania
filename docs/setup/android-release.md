# Signed Android releases

Every release tag `web-vX.Y.Z` runs [`release-android.yml`](../../.github/workflows/release-android.yml).
The web and the app share one version ([ADR 0013](../adr/0013-single-release-version-and-update-check.md)).

The workflow:
1. checks that the tag matches `web/package.json`;
2. builds the release APK and signs it with your keystore;
3. verifies the signature;
4. attaches `musicmania-X.Y.Z.apk` and its `.sha256` file to the tag's GitHub release, creating the
   release if it does not exist yet.

Older apps then offer the update as soon as the API announces the new version.

## One-time setup

### 1. The release keystore
Use the keystore you already sign release builds with. Its SHA-256 fingerprint must be listed in
`assetlinks.json` ([App Links guide](spotify-app-links.md)); otherwise Android does not verify the
sign-in redirect, and logging in fails. If you have none yet:

```bash
keytool -genkeypair -keystore musicmania-release.jks -alias musicmania -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v -keystore musicmania-release.jks -alias musicmania   # copy the SHA-256 into assetlinks.json
```

Keep the keystore and its passwords in a password manager. If you lose them, installed apps cannot be
updated in place: users have to uninstall and install again.

### 2. Encode it for GitHub
GitHub secrets hold text, so store the keystore as base64.

PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\musicmania-release.jks")) | Set-Clipboard
```

Linux or macOS: `base64 -w0 musicmania-release.jks` (on macOS, `base64 -i musicmania-release.jks`).

### 3. The `android-release` environment
Go to *Settings → Environments → New environment* and name it `android-release`.
- **Deployment branches and tags** → *Selected branches and tags* → add the tag rule `web-v*`. Only
  release tags can then read the keystore.
- Optionally, add yourself under **Required reviewers** to approve every signed build.

Add these **environment secrets**:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The base64 text from step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias (for example `musicmania`) |
| `ANDROID_KEY_PASSWORD` | Key password (often the same as the keystore password) |

### 4. Repository variables
These go under *Settings → Secrets and variables → Actions → Variables*.
- `SPOTIFY_CLIENT_ID` and `MUSICMANIA_API_URL` already exist (the web uses them too).
- **Add `SPOTIFY_REDIRECT_URI_ANDROID`**: the app's HTTPS App Link redirect, the same value as
  `SPOTIFY_REDIRECT_URI` in your `mobile/local.properties`. It must also be registered in the Spotify
  dashboard.

## Releasing

1. On `develop`, bump the version with `npm version X.Y.Z --no-git-tag-version` in `web/`.
2. Add the release notes for `X.Y.Z` at the top of
   [`api/release-notes.json`](../../api/release-notes.json): a few short lines (140 characters at most)
   in `en` and `es`.
   - The update dialog shows them to people on an older version.
   - They open the GitHub release text.
   - The API tests fail if the current version has no notes.
3. Open a PR with both changes and merge it.
4. Tag the merge commit `web-vX.Y.Z` and push the tag. This deploys the web and, in parallel, builds
   and attaches the APK.
5. When the APK is on the release, tag `api-vX.Y.Z` (or any new `api-v*`) so the API announces the
   version and older apps offer the update.

To rebuild the APK of an existing release, go to *Actions → Release Android → Run workflow* and choose
the tag, not a branch, under *Use workflow from*. The APK on the release is replaced.

The job prints the signing certificate's SHA-256 as a notice. Compare it with `assetlinks.json` the
first time.
