-- V2__seed.sql
-- BCrypt hash for password 'lap00p00' (cost 12).
-- NOTE : never seed credentials via migration in production. Dev-only.
INSERT INTO app_user (username, password_hash, role) VALUES
    ('matvs', '$2b$12$177OHfOHwTYjHx3dX9f1DeuMAdKNWmxJbika4aOALoBBVacX6RRvW', 'ADMIN');

-- Zürich freepeepees (and one enemy's grave)
INSERT INTO toilet (name, address, location, pin_code, is_working, toilet_type, notes, created_by)
SELECT * FROM (VALUES
    ('McDonald''s Bahnhofplatz',
        'Bahnhofplatz 5, 8001 Zürich',
        ST_SetSRID(ST_MakePoint(8.5395, 47.3775), 4326)::geography,
        '1297', TRUE, 'MCDONALDS'::toilet_type, NULL,
        (SELECT id FROM app_user WHERE username='matvs')),

    ('McDonald''s Langstrasse',
        'Langstrasse 201, 8005 Zürich',
        ST_SetSRID(ST_MakePoint(8.5264, 47.3769), 4326)::geography,
        '0739', TRUE, 'MCDONALDS'::toilet_type, NULL,
        (SELECT id FROM app_user WHERE username='matvs')),

    ('McDonald''s Stadelhofen',
        'Gottfried-Keller-Strasse 7, 8001 Zürich',
        ST_SetSRID(ST_MakePoint(8.5490, 47.3667), 4326)::geography,
        '2233a', TRUE, 'MCDONALDS'::toilet_type, NULL,
        (SELECT id FROM app_user WHERE username='matvs')),

    ('McDonald''s Oerlikon',
        'Hofwiesenstrasse 350-354, 8050 Zürich',
        ST_SetSRID(ST_MakePoint(8.5462, 47.4115), 4326)::geography,
        '1346', TRUE, 'MCDONALDS'::toilet_type, NULL,
        (SELECT id FROM app_user WHERE username='matvs')),

    ('Jaga''s Toi-toi',
        'Eisfeldstrasse 10, 8050 Zürich',
        ST_SetSRID(ST_MakePoint(8.5428, 47.4142), 4326)::geography,
        NULL, TRUE, 'OTHER'::toilet_type,
        'Toi-toi at Jaga''s place entrance. Do not use the wall or else Jan will knock on the window',
        (SELECT id FROM app_user WHERE username='matvs')),

    ('Zürich HB',
        'Bahnhofplatz 15, 8001 Zürich',
        ST_SetSRID(ST_MakePoint(8.5403, 47.3779), 4326)::geography,
        NULL, TRUE, 'PUBLIC'::toilet_type,
        'any long-distance train, that has at least 5 minutes till departures in case of no.1 and 12 minutes in ca. of no.2 and 1 hour 23 minutes in case of Diarrhea',
        (SELECT id FROM app_user WHERE username='matvs')),

    ('Enemy''s grave',
        'Gertrud-Kolmar-Straße 14, 10117 Berlin',
        ST_SetSRID(ST_MakePoint(13.3815, 52.5125), 4326)::geography,
        NULL, FALSE, 'OTHER'::toilet_type, NULL,
        (SELECT id FROM app_user WHERE username='matvs'))
) AS v(name, address, location, pin_code, is_working, toilet_type, notes, created_by);
