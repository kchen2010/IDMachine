-- ──────────────────────────────────────────────────────────────────────────────
-- Seed data for local H2 dev profile (loaded after Hibernate creates the schema)
-- ──────────────────────────────────────────────────────────────────────────────

-- Artist
INSERT INTO artists (id, name, slug, created_at)
VALUES (1, 'Prospa', 'prospa', CURRENT_TIMESTAMP);

-- Tracklists
INSERT INTO tracklists (id, artist_id, title, url, event_date, venue, scraped_at) VALUES
(1, 1, 'Prospa @ fabric London',             'https://www.1001tracklists.com/tracklist/fabric-prospa-2024.html',   '2024-03-15', 'fabric, London', CURRENT_TIMESTAMP),
(2, 1, 'Prospa - Rinse FM Show',             'https://www.1001tracklists.com/tracklist/prospa-rinsefm-2024.html', '2024-01-20', 'Rinse FM',        CURRENT_TIMESTAMP),
(3, 1, 'Prospa @ Circoloco DC10 Ibiza',     'https://www.1001tracklists.com/tracklist/prospa-dc10-2023.html',    '2023-08-07', 'DC10, Ibiza',     CURRENT_TIMESTAMP);

-- Tracklist 1: fabric
--   is_id = true if listed as "ID", own_id = true if it belongs to Prospa
INSERT INTO track_entries (id, tracklist_id, position, track_artist, title, is_id, own_id) VALUES
(1,  1, 1, 'Prospa',       'We Found God (Original Mix)',   false, false),
(2,  1, 2, 'Prospa',       NULL,                            true,  true),   -- Prospa own ID
(3,  1, 3, 'DJ Seinfeld',  'U',                             false, false),
(4,  1, 4, NULL,           NULL,                            true,  false),  -- full ID-ID (unknown artist)
(5,  1, 5, 'KETTAMA',      NULL,                            true,  false),  -- ID by KETTAMA
(6,  1, 6, 'Prospa',       NULL,                            true,  true);   -- another Prospa own ID

-- Tracklist 2: Rinse FM
INSERT INTO track_entries (id, tracklist_id, position, track_artist, title, is_id, own_id) VALUES
(7,  2, 1, 'Interceps',     'Phantom Limb',                 false, false),
(8,  2, 2, 'Prospa',        NULL,                            true,  true),  -- Prospa own ID
(9,  2, 3, 'Prospa',        'Here I Am',                     false, false),
(10, 2, 4, 'Joy Anonymous', NULL,                            true,  false), -- other artist ID
(11, 2, 5, NULL,            NULL,                            true,  false), -- full ID-ID
(12, 2, 6, 'Prospa',        NULL,                            true,  true);  -- Prospa own ID

-- Tracklist 3: DC10
INSERT INTO track_entries (id, tracklist_id, position, track_artist, title, is_id, own_id) VALUES
(13, 3, 1, 'Joseph Disco',  'Swirling',                     false, false),
(14, 3, 2, NULL,            NULL,                            true,  false), -- full ID-ID
(15, 3, 3, 'Prospa',        NULL,                            true,  true),  -- Prospa own ID
(16, 3, 4, 'DJ Seinfeld',   NULL,                            true,  false), -- other artist ID
(17, 3, 5, 'Prospa',        'Tell Me (feat. Zara Kershaw)',  false, false);
