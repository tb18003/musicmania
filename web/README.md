# MusicMania Web

The installable web app (PWA) for iPhone and any modern browser. TypeScript + Svelte 5 + Vite,
published to GitHub Pages at `https://<owner>.github.io/musicmania/`.
Why and how: [ADR 0012](../docs/adr/0012-web-client-pwa.md) · deployment: [web-deploy.md](../docs/setup/web-deploy.md).

## Commands

Node 24+ (`.nvmrc`). Run from `web/`:

```bash
npm ci                 # install exactly what package-lock.json says
npm run dev            # dev server at http://127.0.0.1:5173/musicmania/
npm run verify         # everything CI runs: lint, types, API contract, tests, build, e2e
npm run lint           # ESLint (layer rules) + Prettier + licence headers
npm run format         # fix formatting and add missing licence headers
npm run check          # svelte-check (TypeScript strict)
npm test               # Vitest unit/component tests with coverage
npm run test:e2e       # Playwright on WebKit (iPhone 15) against the production build
npm run api:types      # regenerate src/lib/data/cloud/schema.d.ts from api/openapi.yaml
npm run icons          # re-render the PNG icons from public/icons/icon.svg
```

First run of the e2e tests: `npx playwright install webkit` (and `chromium` for `npm run icons`).

For local sign-in, copy `.env.example` to `.env.local` and fill in your Spotify Client ID; add
`http://127.0.0.1:5173/musicmania/auth/callback` as a redirect URI in the Spotify dashboard.

## Layout

| Path             | Contents                                                    | May import        |
| ---------------- | ----------------------------------------------------------- | ----------------- |
| `src/lib/model`  | Immutable domain types                                      | nothing           |
| `src/lib/domain` | Pure logic ported from `core:domain`, repository interfaces | model             |
| `src/lib/data`   | Spotify and API clients, IndexedDB, importer                | model, domain     |
| `src/lib/ui`     | Design system: tokens, components, i18n (EN/ES), motion     | model, domain     |
| `src/features`   | Screens                                                     | model, domain, ui |
| `src/app`        | Shell, router, dependency wiring                            | everything        |

The rules are enforced by ESLint (`eslint.config.js`), like Konsist in the Android app.
Every animation respects the reduced-motion preference; entrance animations play once per session.
