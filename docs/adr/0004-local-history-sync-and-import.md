# ADR 0004 — Local listening history: background sync + data-export import

- Status: Accepted · Date: 2026-11-19

## Context
Spotify's API exposes only rankings (`/me/top`) and the **last 50** plays. Lifetime stats
(minutes, streaks, clock, per-item history) are impossible from the API alone. Competing apps
upload users' data to their servers.

## Decision
- Keep an on-device `play_events` table as the single source of truth for statistics.
- A WorkManager job (≈ every 60 min, network + battery constraints, exponential backoff) pulls
  `recently-played` with a cursor and inserts new plays; it also refreshes daily top snapshots.
- Users can import Spotify's **Extended Streaming History** (and the basic export) from a
  `.zip`/`.json` chosen through the Storage Access Framework. Parsing is streamed; defensive
  limits defeat zip bombs; privacy-sensitive fields are ignored.
- Plays are de-duplicated with a key of normalised track/artist + end second, so sync and
  import can overlap safely.
- Aggregations run in SQLite for performance on hundreds of thousands of rows.

## Consequences
+ Free lifetime stats, fully private, works offline.
− Plays made while the device is offline for >50 tracks between syncs can be missed until the
  next import; synced plays use track duration as an estimate of time played.
