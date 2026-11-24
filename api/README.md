# MusicMania — API

Kotlin/Ktor service behind the friends leaderboard. It is the **only** client of the cloud
database (`../database`); the apps talk to it with the Spotify token they already have.

```
api/
├── src/main/kotlin/…/api
│   ├── Application.kt      server, plugins (rate limit, problems, logging) and wiring
│   ├── auth/               Spotify token verification, cache, allowlist check, /v1 guard
│   ├── db/                 connection pool, transactions, SQL repositories
│   ├── model/              request/response models and input validation
│   └── routes/             /v1 endpoints
├── src/test/kotlin/…       unit tests + integration tests (embedded Postgres 17 + fake Spotify)
├── openapi.yaml            the HTTP contract
└── Dockerfile              multi-stage image (distroless JRE 21, non-root)
```

## How a request is authenticated

1. The client sends `Authorization: Bearer <Spotify access token>`.
2. The API hashes the token (SHA-256) and looks it up in an in-memory cache (valid: 5 min,
   rejected: 30 s). On a miss it calls Spotify `GET /v1/me` to learn the Spotify user id.
3. The id must be in `musicmania.allowlist` and enabled; otherwise `403 not_allowlisted`.
4. Every write uses the id from the verified token, never an id from the request body.

Tokens and the `Authorization` header are never logged or stored. Spotify outages return
`503` with `Retry-After`, not `401`, so clients do not discard a good token.

> Spotify's `/v1/me` does not say which app issued a token, so any valid token of an
> allow-listed user is accepted. Getting one requires that user's own Spotify session.

## Endpoints

| Method | Path | |
|---|---|---|
| GET | `/health` | Liveness (public) |
| GET | `/v1/me` | My profile; created private on the first call |
| PUT | `/v1/me/settings` | `{ "sharingEnabled": true }` — opt in/out of the rankings |
| PUT | `/v1/me/stats` | Upload aggregated stats per period (idempotent, max 64 KB) |
| DELETE | `/v1/me` | Delete my profile and stats |
| GET | `/v1/leaderboards?metric=&period=&start=` | Ranking of people who share |
| GET | `/v1/users/{id}/stats?period=&start=` | A sharing user's stats and top items |

Full contract: [`openapi.yaml`](openapi.yaml).

## Configuration (environment variables)

| Variable | Required | |
|---|---|---|
| `DATABASE_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://<host>:5432/postgres?sslmode=verify-full&sslrootcert=/secrets/supabase-ca.crt` |
| `DATABASE_USER` | yes | `musicmania_api` (least-privileged role created by the migration) |
| `DATABASE_PASSWORD` | yes | From Secret Manager; never commit it |
| `PORT` | no | Defaults to `8080` (Cloud Run sets it) |
| `SPOTIFY_API_BASE_URL` | no | Defaults to `https://api.spotify.com` (overridden in tests) |
| `CORS_ORIGINS` | no | Comma-separated `https://` origins for a future web client; empty disables CORS |

See [`.env.example`](.env.example).

## Commands

Requires JDK 21 (Android Studio's JBR works). No Docker needed for the tests: they download an
embedded Postgres 17 and apply `../database/supabase/migrations`.

```bash
cd api
./gradlew test                     # unit + integration tests
./gradlew spotlessApply            # format + license headers
./gradlew spotlessCheck detekt     # static analysis (same rules as mobile/)
./gradlew run                      # run locally (needs the environment variables above)
./gradlew buildFatJar              # build/libs/musicmania-api.jar
docker build -t musicmania-api .   # container image
```

To run it against a local database: `cd ../database && supabase db start`, then in the SQL
editor or `psql` run `alter role musicmania_api with password 'change-me';` and start the API
with the values from `.env.example`.

## Deployment

Target: Google Cloud Run (`europe-southwest1`, scale to zero, max 2 instances), with the database
password in Secret Manager and GitHub Actions authenticating through Workload Identity Federation
(no JSON keys). The step-by-step guide and the deploy workflow come with the cloud setup.
