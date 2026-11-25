# ADR 0010 — Supabase Postgres behind an own API for the friends ranking

- Status: Accepted · Date: 2026-11-24

## Context
MusicMania was 100 % local. Friends wanted to compare their listening, which needs shared state
somewhere. Constraints:
- **Privacy:** only opt-in aggregates may leave a device, never the listening history (security rule 10).
- **Scale:** at most 5 people (Spotify Development Mode), so the service must cost nothing and scale to zero.
- Clients must **never talk to the database directly**, and nobody may read or write without a login.

## Decision
- **Database: Postgres on Supabase** (free tier, Postgres 17). A ranking is relational: one
  `RANK() OVER (PARTITION BY period ORDER BY metric DESC)` view gives competition ranks with
  ties. Check constraints keep values in range, and migrations are versioned SQL tested with
  pgTAP (`database/`).
- **A dedicated `musicmania` schema that the Supabase Data API cannot reach:**
  - it is not an exposed schema;
  - `anon`, `authenticated` and `service_role` have no grants;
  - row-level security is on every table as defence in depth.

  The public anon key is useless against it.
- **Least privilege:** only the MusicMania API connects, as role `musicmania_api`:
  - it can only `SELECT` the allowlist;
  - it has CRUD on profiles, stats and top items;
  - it has no DDL.

  The password lives in GCP Secret Manager. The connection uses TLS with `sslmode=verify-full`
  against Supabase's CA, through the IPv4 session pooler.
- **Data model:**
  - `allowlist` (managed by hand in the SQL editor);
  - `profiles` (`sharing_enabled` off by default);
  - `stat_periods` (week/month/year/all-time totals);
  - `top_items` (top 10 names);
  - a `leaderboard` view that only includes enabled users who share.
- **API in front** ([ADR 0011](0011-spotify-token-auth-and-allowlist.md)): Kotlin/Ktor on Cloud
  Run (scale to zero, `max-instances=1`, budget alert). It validates every upload; the bounds
  match the database checks.

## Consequences
+ Zero cost at this scale; the database is closed to everything but the API.
+ Schema, permissions and the ranking view are tested in CI (pgTAP) and by the API's integration
  tests, which run the real migrations on an embedded Postgres 17.
− The Supabase free plan pauses projects after a week without activity. Uploads after each sync
  keep it awake while someone shares. If it is paused, the API answers 503 and the apps keep
  working locally until it is resumed from the dashboard.
− Allowlist changes need SQL in the Supabase dashboard (documented in `database/README.md`).
