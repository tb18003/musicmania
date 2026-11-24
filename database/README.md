# MusicMania — cloud database

Postgres schema (hosted on [Supabase](https://supabase.com)) behind the friends leaderboard.
Only the MusicMania API connects to it; the apps never talk to the database directly.

```
database/
└── supabase/
    ├── config.toml      local stack settings (the musicmania schema is NOT exposed by the Data API)
    ├── migrations/      versioned schema changes, applied in order
    ├── tests/           pgTAP tests (`supabase test db`)
    └── seed.sql         fictional users for local development only
```

## Data model

| Table | Contents |
|---|---|
| `musicmania.allowlist` | Who may use the API: Spotify user id + e-mail (label for the admin), role, enabled. **Managed by hand.** |
| `musicmania.profiles` | Display name, avatar and the **opt-in** `sharing_enabled` flag (off by default). |
| `musicmania.stat_periods` | Aggregates per calendar period (`week`, `month`, `year`, `all_time`): minutes, streams, unique artists/tracks, streaks. Never individual plays. |
| `musicmania.top_items` | Top 10 artists and tracks per period (names only). |
| `musicmania.leaderboard` (view) | Competition ranks (1, 1, 3) per metric, only for users who share and are enabled. |

## Access model

- The `musicmania` schema is closed to the Supabase API roles (`anon`, `authenticated`,
  `service_role`) and is not in the exposed schemas, so the public anon key cannot read it.
- The API connects as **`musicmania_api`**: read-only on the allowlist, read/write on profiles,
  stats and top items, no DDL. Row-level security is enabled on every table as defence in depth.
- Spotify does not give the user's e-mail to Development Mode apps, so the API identifies people
  by their **Spotify user id** (from the verified Spotify token). The app shows each user their
  id in *Settings → Ranking* so they can send it to you.

## Managing the allowlist (Supabase dashboard → SQL editor)

```sql
-- Give access
insert into musicmania.allowlist (spotify_user_id, email, display_name)
values ('spotify_user_id_here', 'friend@example.com', 'Ana');

-- Revoke access (keeps their data, hides them from rankings)
update musicmania.allowlist set enabled = false where email = 'friend@example.com';

-- Remove completely (also deletes their profile and stats)
delete from musicmania.allowlist where email = 'friend@example.com';
```

## Local development

Requires Docker and the [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started) 3.x.
CLI v3 targets the **local** stack unless you pass `--remote`.

```bash
cd database
supabase db start          # Postgres with the migrations and seed applied
supabase test db           # pgTAP tests in supabase/tests
supabase db reset          # rebuild the local database from scratch
supabase stop
```

## Deploying to the hosted project

Full walkthrough (schema, API role password, Cloud Run): [`docs/setup/cloud-deploy.md`](../docs/setup/cloud-deploy.md).

One-time setup:
1. Create a Supabase project (region close to your users, e.g. `eu-west-3` Paris) and keep the
   database password in a password manager.
2. *Project Settings → Data API*: make sure `musicmania` is **not** in the exposed schemas.
3. `supabase login` and `supabase link --project-ref <ref>` from `database/`.

Apply pending migrations (explicit, never automatic):

```bash
cd database
supabase db push --remote          # asks for confirmation; add --yes only in a protected CI job
```

Then set the API role's password once, in the SQL editor (keep it in Secret Manager for the API):

```sql
alter role musicmania_api with password 'a-long-random-password';
```

## Writing a migration

1. `supabase migration new <short_name>` and write the SQL (keep it portable: the API tests run
   the same files on a plain Postgres, which has no Supabase roles).
2. Add or update pgTAP tests in `supabase/tests/`.
3. `supabase db reset && supabase test db` locally, or let CI run them.
4. Never edit a migration that has already been pushed to the hosted project: add a new one.
