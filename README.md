# MusicMania

**Your music, beautifully decoded.** An open-source Android app that turns your Spotify
listening into rich, animated statistics — tops with live ranking changes, your lifetime
history, a 24-hour listening clock, streaks, milestones and a story-style recap you can
open any day of the year.

Private by design: everything is computed and stored **on your device**. No servers, no
tracking, no ads.

> MusicMania is an independent project, not affiliated with or endorsed by Spotify AB.

## Features

| | |
|---|---|
| 🏆 **Tops** | Tracks, artists, albums and genres for 4 weeks / 6 months / 1 year / all time, with ▲▼ NEW movement versus the previous snapshot |
| 🎧 **Now playing** | Live card that tints itself with the album art, with smooth progress |
| 🕘 **History** | Every play, grouped by day, kept up to date by an hourly background sync |
| 📦 **Lifetime import** | Import Spotify's *Extended Streaming History* (`.zip` or `.json`) for free, on device |
| 🧭 **Insights** | Listening clock, weekday × hour heatmap, streaks, skip/shuffle rates, platforms, period comparison, milestones |
| ✨ **Recap** | "Wrapped, any time": animated stories for any month or year, with a shareable card |
| 🔎 **Details** | Per track/artist/album: your streams, minutes, first/last listen, monthly chart |
| ➕ **Playlists** | Turn your top tracks into a private Spotify playlist |
| 📱 **Widget** | Today's minutes and this week's top artist on your home screen |
| 🎨 **Design** | Dark-first minimal UI, shared-element transitions, animated charts, Material You option, *reduce motion* support, English & Spanish |
| 🧪 **Demo mode** | Explore everything with generated sample data, no account needed |

## Getting started

### Requirements
- Android Studio (recent) or JDK 21, Android SDK 36.
- A device or emulator running Android 8.0 (API 26) or newer.

### Build and run (demo mode)
```bash
git clone https://github.com/tb18003/spotistats.git
cd spotistats
cp local.properties.example local.properties   # set sdk.dir
./gradlew installDebug
```
Open the app and tap **Explore with demo data**.

### Connect your Spotify account
Spotify now requires an HTTPS redirect, so a one-time setup is needed:

1. Create an app at the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
   (the owner needs Spotify Premium) and select *Web API*.
2. Choose a redirect URL you control, e.g. `https://<you>.github.io/musicmania/callback`,
   and register it in the dashboard.
3. Publish `assetlinks.json` for that domain so Android verifies the App Link — full guide in
   [`docs/setup/spotify-app-links.md`](docs/setup/spotify-app-links.md).
4. Add up to 5 users (Development Mode limit) under *User Management*.
5. Put your values in `local.properties`:
   ```properties
   SPOTIFY_CLIENT_ID=your_client_id
   SPOTIFY_REDIRECT_URI=https://you.github.io/musicmania/callback
   ```

No client secret is used — the app uses the OAuth Authorization Code flow with PKCE.

### Import your lifetime history
In the app: *Settings → Import streaming history* and follow the steps. Request
**Extended streaming history** on Spotify's [privacy page](https://www.spotify.com/account/privacy/);
the export can take a few days to arrive.

## Architecture

Multi-module Clean Architecture with MVVM/UDF, offline-first Room database, Hilt DI and
Jetpack Compose. See [`CLAUDE.md`](CLAUDE.md) for the full guide and
[`docs/adr`](docs/adr) for the key decisions.

```
app → feature:* → core:domain ← core:data → core:network / core:database / core:datastore
                 ↘ core:ui → core:designsystem
```

## Quality

```bash
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest test koverHtmlReport
```
Unit, integration (Room/MockWebServer/Robolectric), architecture (Konsist) and screenshot
(Roborazzi) tests run in CI on every pull request.

Instrumented tests (Android Keystore + Tink, Room schema, OAuth App Link resolution and an
end-to-end demo journey) run on a device or on a Gradle Managed Device:

```bash
./gradlew connectedDebugAndroidTest      # on a connected device or running emulator
./gradlew pixel6Api34DebugAndroidTest    # on a managed emulator (downloads an ATD image)
```

## Security

Tokens are encrypted with Google Tink (AES-256-GCM) under an Android Keystore key, never
logged and excluded from backups. Read [`SECURITY.md`](SECURITY.md) for the threat model and
how to report vulnerabilities.

## Contributing

Contributions are welcome! Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) and the
[code of conduct](CODE_OF_CONDUCT.md). Work happens on `develop`; `master` only holds releases.

## License

Copyright (C) 2026 MusicMania contributors.

This program is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. See [`LICENSE`](LICENSE).

Third-party: Space Grotesk font © The Space Grotesk Project Authors, SIL Open Font License 1.1
([`third_party/SpaceGrotesk-OFL.txt`](third_party/SpaceGrotesk-OFL.txt)).
