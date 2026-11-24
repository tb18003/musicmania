-- Local development data only: fictional users to try the API and the leaderboard.
-- `supabase db push --remote` does not run this file.

insert into musicmania.allowlist (spotify_user_id, email, display_name) values
    ('demo_ana', 'ana@example.com', 'Ana'),
    ('demo_bruno', 'bruno@example.com', 'Bruno'),
    ('demo_carla', 'carla@example.com', 'Carla');

insert into musicmania.profiles (spotify_user_id, display_name, sharing_enabled) values
    ('demo_ana', 'Ana', true),
    ('demo_bruno', 'Bruno', true),
    ('demo_carla', 'Carla', false);

insert into musicmania.stat_periods
    (spotify_user_id, period_kind, period_start, minutes, streams, unique_artists, unique_tracks, current_streak, longest_streak)
values
    ('demo_ana', 'week', date_trunc('week', current_date)::date, 1240, 410, 58, 230, 12, 40),
    ('demo_bruno', 'week', date_trunc('week', current_date)::date, 980, 350, 71, 260, 5, 21),
    ('demo_carla', 'week', date_trunc('week', current_date)::date, 1500, 520, 33, 140, 30, 30);

insert into musicmania.top_items (spotify_user_id, period_kind, period_start, item_type, position, name) values
    ('demo_ana', 'week', date_trunc('week', current_date)::date, 'artist', 1, 'Nova Lights'),
    ('demo_bruno', 'week', date_trunc('week', current_date)::date, 'artist', 1, 'Velvet Static');
