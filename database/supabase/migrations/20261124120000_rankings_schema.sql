-- MusicMania cloud schema: listening rankings between allow-listed users.
-- Licensed under GPL-3.0-or-later. See database/README.md for the access model.
--
-- Design notes
-- * Everything lives in the dedicated `musicmania` schema, which is NOT exposed through the
--   Supabase Data API (see supabase/config.toml) and is closed to the Supabase API roles.
-- * Only the MusicMania API connects, as the least-privileged `musicmania_api` role.
-- * Only aggregates leave the phones: no raw listening history is ever stored here.

-- Supabase already has the `extensions` schema; plain Postgres (API tests) does not.
create schema if not exists extensions;
create extension if not exists citext with schema extensions;

create schema if not exists musicmania;
revoke all on schema musicmania from public;

-- ---------------------------------------------------------------- tables

-- Who may use the API. Maintained by hand (SQL editor); the API can only read it.
-- Spotify no longer returns the e-mail address to Development Mode apps, so the Spotify user
-- id is what gets checked; `email` is a human-readable label for the admin.
create table musicmania.allowlist (
    spotify_user_id text primary key
        check (spotify_user_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    email extensions.citext not null unique
        check (char_length(email::text) <= 254 and email::text like '%_@_%'),
    display_name text check (char_length(display_name) <= 100),
    role text not null default 'member' check (role in ('member', 'admin')),
    enabled boolean not null default true,
    note text check (char_length(note) <= 500),
    created_at timestamptz not null default now()
);

comment on table musicmania.allowlist is
    'Manually managed list of Spotify accounts allowed to use the API. The API has read-only access.';

-- Public profile of an allow-listed user, created on the first authenticated API call.
create table musicmania.profiles (
    spotify_user_id text primary key
        references musicmania.allowlist (spotify_user_id) on delete cascade,
    display_name text check (char_length(display_name) <= 100),
    avatar_url text check (char_length(avatar_url) <= 2048 and avatar_url like 'https://%'),
    -- Opt-in: nothing a user publishes is visible to others until they enable sharing.
    sharing_enabled boolean not null default false,
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

-- Aggregated listening statistics per calendar period (never individual plays).
create table musicmania.stat_periods (
    spotify_user_id text not null
        references musicmania.profiles (spotify_user_id) on delete cascade,
    period_kind text not null check (period_kind in ('week', 'month', 'year', 'all_time')),
    period_start date not null,
    minutes integer not null check (minutes >= 0),
    streams integer not null check (streams between 0 and 10000000),
    unique_artists integer not null check (unique_artists between 0 and 1000000),
    unique_tracks integer not null check (unique_tracks between 0 and 10000000),
    current_streak integer not null default 0 check (current_streak between 0 and 36600),
    longest_streak integer not null default 0 check (longest_streak between 0 and 36600),
    updated_at timestamptz not null default now(),
    primary key (spotify_user_id, period_kind, period_start),
    constraint stat_periods_streaks_consistent check (current_streak <= longest_streak),
    -- Periods are calendar-aligned so that everyone is ranked on exactly the same window.
    constraint stat_periods_aligned_start check (
        case period_kind
            when 'week' then extract(isodow from period_start) = 1
            when 'month' then extract(day from period_start) = 1
            when 'year' then extract(month from period_start) = 1 and extract(day from period_start) = 1
            else period_start = date '1970-01-01'
        end
    ),
    -- A period cannot hold more listening time than it has minutes.
    constraint stat_periods_minutes_fit check (
        minutes <= case period_kind
            when 'week' then 7 * 24 * 60
            when 'month' then 31 * 24 * 60
            when 'year' then 366 * 24 * 60
            else 100 * 366 * 24 * 60
        end
    )
);

-- Top artists and tracks of a user for a period (names only, as shown in the app).
create table musicmania.top_items (
    spotify_user_id text not null,
    period_kind text not null,
    period_start date not null,
    item_type text not null check (item_type in ('artist', 'track')),
    position smallint not null check (position between 1 and 10),
    name text not null check (char_length(name) between 1 and 200),
    spotify_id text check (spotify_id ~ '^[A-Za-z0-9]{1,64}$'),
    primary key (spotify_user_id, period_kind, period_start, item_type, position),
    foreign key (spotify_user_id, period_kind, period_start)
        references musicmania.stat_periods (spotify_user_id, period_kind, period_start) on delete cascade
);

-- ---------------------------------------------------------------- updated_at

create function musicmania.touch_updated_at() returns trigger
    language plpgsql
    set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create trigger stat_periods_touch_updated_at
    before update on musicmania.stat_periods
    for each row execute function musicmania.touch_updated_at();

-- ---------------------------------------------------------------- leaderboard

-- One row per sharing, enabled user and period, with a competition rank ("1, 1, 3") per metric.
-- security_invoker: the caller's privileges and row-level security apply, not the owner's.
create view musicmania.leaderboard
    with (security_invoker = true)
as
select
    s.period_kind,
    s.period_start,
    s.spotify_user_id,
    coalesce(p.display_name, a.display_name) as display_name,
    p.avatar_url,
    s.minutes,
    s.streams,
    s.current_streak,
    s.unique_artists,
    rank() over (partition by s.period_kind, s.period_start order by s.minutes desc) as minutes_rank,
    rank() over (partition by s.period_kind, s.period_start order by s.streams desc) as streams_rank,
    rank() over (partition by s.period_kind, s.period_start order by s.current_streak desc) as streak_rank,
    rank() over (partition by s.period_kind, s.period_start order by s.unique_artists desc) as unique_artists_rank
from musicmania.stat_periods s
join musicmania.profiles p using (spotify_user_id)
join musicmania.allowlist a using (spotify_user_id)
where p.sharing_enabled and a.enabled;

-- ---------------------------------------------------------------- access control

-- Defence in depth: row-level security on, with policies only for the API role, so any other
-- role that somehow got table privileges would still see nothing.
alter table musicmania.allowlist enable row level security;
alter table musicmania.profiles enable row level security;
alter table musicmania.stat_periods enable row level security;
alter table musicmania.top_items enable row level security;

-- The API's database role: log in with a password set outside of migrations
-- (`alter role musicmania_api with password '…'`, see database/README.md). No DDL rights.
do $$
begin
    if not exists (select from pg_roles where rolname = 'musicmania_api') then
        create role musicmania_api with login noinherit;
    end if;
end
$$;

grant usage on schema musicmania to musicmania_api;
grant select on musicmania.allowlist to musicmania_api;
grant select, insert, update, delete on musicmania.profiles, musicmania.stat_periods, musicmania.top_items
    to musicmania_api;
grant select on musicmania.leaderboard to musicmania_api;

create policy api_reads_allowlist on musicmania.allowlist
    for select to musicmania_api using (true);
-- Ownership (a user only writes their own rows) is enforced by the API, which takes the user id
-- from the verified Spotify token, never from the request body.
create policy api_manages_profiles on musicmania.profiles
    for all to musicmania_api using (true) with check (true);
create policy api_manages_stats on musicmania.stat_periods
    for all to musicmania_api using (true) with check (true);
create policy api_manages_top_items on musicmania.top_items
    for all to musicmania_api using (true) with check (true);

-- Close the schema to the Supabase API roles when they exist (they do not in a plain Postgres,
-- e.g. the API's integration tests).
do $$
declare
    api_role text;
begin
    foreach api_role in array array['anon', 'authenticated', 'service_role'] loop
        if exists (select from pg_roles where rolname = api_role) then
            execute format('revoke all on schema musicmania from %I', api_role);
            execute format('revoke all on all tables in schema musicmania from %I', api_role);
            execute format('revoke all on all functions in schema musicmania from %I', api_role);
            execute format('alter default privileges in schema musicmania revoke all on tables from %I', api_role);
            execute format('alter default privileges in schema musicmania revoke all on functions from %I', api_role);
        end if;
    end loop;
end
$$;

revoke all on function musicmania.touch_updated_at() from public;
