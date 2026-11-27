-- MusicMania cloud schema: cover art for the friends' top lists.
-- Licensed under GPL-3.0-or-later. See database/README.md for the access model.
--
-- The apps upload the public Spotify CDN URL of each top-10 cover so the ranking can show it.
-- It is a link to a public image, not personal data; rows uploaded before this column stay null
-- until each friend's next sync.

alter table musicmania.top_items
    add column image_url text
        check (image_url is null or (char_length(image_url) <= 2048 and image_url like 'https://%'));

comment on column musicmania.top_items.image_url is 'Public HTTPS cover URL (Spotify CDN), or null.';
