-- pgTAP tests for the rankings schema. Run with `supabase test db` (see database/README.md).
begin;
create extension if not exists pgtap with schema extensions;
set search_path = extensions, public;

select plan(28);

-- ---------------------------------------------------------------- structure
select has_schema('musicmania');
select has_table('musicmania', 'allowlist', 'allowlist table exists');
select has_table('musicmania', 'profiles', 'profiles table exists');
select has_table('musicmania', 'stat_periods', 'stat_periods table exists');
select has_table('musicmania', 'top_items', 'top_items table exists');
select has_view('musicmania', 'leaderboard', 'leaderboard view exists');

-- ---------------------------------------------------------------- access control
select ok(not has_schema_privilege('anon', 'musicmania', 'usage'), 'anon cannot use the schema');
select ok(not has_schema_privilege('authenticated', 'musicmania', 'usage'), 'authenticated cannot use the schema');
select ok(not has_table_privilege('anon', 'musicmania.stat_periods', 'select'), 'anon cannot read stats');
select ok(not has_table_privilege('authenticated', 'musicmania.leaderboard', 'select'), 'authenticated cannot read the leaderboard');
select ok(has_table_privilege('musicmania_api', 'musicmania.allowlist', 'select'), 'API role reads the allowlist');
select ok(not has_table_privilege('musicmania_api', 'musicmania.allowlist', 'insert'), 'API role cannot add itself to the allowlist');
select ok(not has_table_privilege('musicmania_api', 'musicmania.allowlist', 'update'), 'API role cannot change the allowlist');
select ok(
    has_table_privilege('musicmania_api', 'musicmania.stat_periods', 'insert')
    and has_table_privilege('musicmania_api', 'musicmania.stat_periods', 'update')
    and has_table_privilege('musicmania_api', 'musicmania.stat_periods', 'delete'),
    'API role manages stats'
);
select ok(
    (select bool_and(relrowsecurity) from pg_class
     where relnamespace = 'musicmania'::regnamespace and relkind = 'r'),
    'row-level security is enabled on every table'
);

-- ---------------------------------------------------------------- fixtures
-- Start from an empty schema (the local seed may hold rows for this same week). Rolled back at the end.
delete from musicmania.allowlist;
insert into musicmania.allowlist (spotify_user_id, email, display_name, enabled) values
    ('t_one', 'one@example.com', 'One', true),
    ('t_two', 'two@example.com', 'Two', true),
    ('t_three', 'three@example.com', 'Three', true),
    ('t_private', 'private@example.com', 'Private', true),
    ('t_disabled', 'disabled@example.com', 'Disabled', false);
insert into musicmania.profiles (spotify_user_id, sharing_enabled) values
    ('t_one', true), ('t_two', true), ('t_three', true), ('t_private', false), ('t_disabled', true);
insert into musicmania.stat_periods
    (spotify_user_id, period_kind, period_start, minutes, streams, unique_artists, unique_tracks, current_streak, longest_streak)
values
    ('t_one', 'week', '2026-11-23', 500, 100, 10, 50, 3, 9),
    ('t_two', 'week', '2026-11-23', 500, 120, 12, 60, 1, 4),
    ('t_three', 'week', '2026-11-23', 200, 90, 30, 70, 7, 7),
    ('t_private', 'week', '2026-11-23', 9000, 9000, 99, 999, 99, 99),
    ('t_disabled', 'week', '2026-11-23', 8000, 8000, 98, 998, 98, 98);

-- ---------------------------------------------------------------- constraints
select throws_ok(
    $$insert into musicmania.stat_periods (spotify_user_id, period_kind, period_start, minutes, streams, unique_artists, unique_tracks)
      values ('t_one', 'week', '2026-11-24', 1, 1, 1, 1)$$,
    '23514', null, 'a week must start on a Monday'
);
select throws_ok(
    $$insert into musicmania.stat_periods (spotify_user_id, period_kind, period_start, minutes, streams, unique_artists, unique_tracks)
      values ('t_one', 'month', '2026-11-01', 50000, 1, 1, 1)$$,
    '23514', null, 'a month cannot hold more minutes than it has'
);
select throws_ok(
    $$insert into musicmania.stat_periods (spotify_user_id, period_kind, period_start, minutes, streams, unique_artists, unique_tracks)
      values ('t_one', 'year', '2026-01-01', -1, 1, 1, 1)$$,
    '23514', null, 'minutes cannot be negative'
);
select throws_ok(
    $$insert into musicmania.top_items (spotify_user_id, period_kind, period_start, item_type, position, name)
      values ('t_one', 'week', '2026-11-23', 'artist', 11, 'Too far')$$,
    '23514', null, 'top lists hold at most ten items'
);
select throws_ok(
    $$insert into musicmania.top_items (spotify_user_id, period_kind, period_start, item_type, position, name, image_url)
      values ('t_one', 'week', '2026-11-23', 'artist', 9, 'Plain link', 'http://i.scdn.co/image/x')$$,
    '23514', null, 'cover URLs must use HTTPS'
);
select throws_ok(
    format($$insert into musicmania.top_items (spotify_user_id, period_kind, period_start, item_type, position, name, image_url)
      values ('t_one', 'week', '2026-11-23', 'artist', 9, 'Huge link', %L)$$, 'https://' || repeat('a', 2041)),
    '23514', null, 'cover URLs hold at most 2048 characters'
);
select lives_ok(
    $$insert into musicmania.top_items (spotify_user_id, period_kind, period_start, item_type, position, name, image_url)
      values ('t_one', 'week', '2026-11-23', 'artist', 9, 'With cover', 'https://i.scdn.co/image/ab67616d0000b273')$$,
    'an HTTPS cover URL is stored'
);
select throws_ok(
    $$insert into musicmania.allowlist (spotify_user_id, email) values ('x', 'not-an-email')$$,
    '23514', null, 'allowlist e-mails must look like e-mails'
);

-- ---------------------------------------------------------------- leaderboard
select results_eq(
    $$select spotify_user_id, minutes_rank from musicmania.leaderboard
      where period_kind = 'week' and period_start = '2026-11-23' order by minutes_rank, spotify_user_id$$,
    $$values ('t_one', 1::bigint), ('t_two', 1::bigint), ('t_three', 3::bigint)$$,
    'ties share a rank and the next rank is skipped (1, 1, 3)'
);
select is_empty(
    $$select 1 from musicmania.leaderboard where spotify_user_id in ('t_private', 't_disabled')$$,
    'users who do not share, or are disabled in the allowlist, never appear'
);
select is(
    (select unique_artists_rank from musicmania.leaderboard where spotify_user_id = 't_three'),
    1::bigint,
    'each metric has its own ranking'
);

-- ---------------------------------------------------------------- API role at runtime
-- The test session needs membership in the API role to impersonate it.
do $$
begin
    execute format('grant musicmania_api to %I', current_user);
exception when others then
    null; -- already a member, or a superuser
end
$$;
-- pgTAP lives in the `extensions` schema, which the API role cannot see, so the statement runs
-- as the API role and the assertion runs after switching back. A privilege error would abort.
set local role musicmania_api;
update musicmania.stat_periods set minutes = 501 where spotify_user_id = 't_one';
reset role;
select is(
    (select minutes from musicmania.stat_periods where spotify_user_id = 't_one'),
    501,
    'API role can update stats (row-level security policies allow it)'
);

-- ---------------------------------------------------------------- cascade
delete from musicmania.allowlist where spotify_user_id = 't_one';
select is_empty(
    $$select 1 from musicmania.stat_periods where spotify_user_id = 't_one'
      union all select 1 from musicmania.profiles where spotify_user_id = 't_one'$$,
    'removing someone from the allowlist deletes their profile and stats'
);

select * from finish();
rollback;
