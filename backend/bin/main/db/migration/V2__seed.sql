-- V2__seed.sql
-- BCrypt hash for password 'lap00p00' (cost 12). Verify with any bcrypt tool.
-- NOTE : in production, never seed credentials via migration. This is dev-only.
INSERT INTO app_user (username, password_hash, role) VALUES
    ('matvs', '$2b$12$177OHfOHwTYjHx3dX9f1DeuMAdKNWmxJbika4aOALoBBVacX6RRvW', 'ADMIN');

-- Seed a handful of Zurich freepeepees (lon, lat)
INSERT INTO toilet (name, address, location, pin_code, is_working, toilet_type, notes, created_by)
SELECT * FROM (VALUES
    ('McDonald''s Bahnhofstrasse', 'Bahnhofstrasse 75, 8001 Zurich',
        ST_SetSRID(ST_MakePoint(8.5402, 47.3760), 4326)::geography, NULL, TRUE, 'MCDONALDS'::toilet_type,
        'Upstairs, often crowded weekends', (SELECT id FROM app_user WHERE username='matvs')),
    ('Coop City St. Annahof', 'Bahnhofstrasse 57, 8001 Zurich',
        ST_SetSRID(ST_MakePoint(8.5395, 47.3744), 4326)::geography, NULL, TRUE, 'OTHER'::toilet_type,
        'Free, 3rd floor near cafeteria', (SELECT id FROM app_user WHERE username='matvs')),
    ('Hauptbahnhof Public WC', 'Bahnhofplatz, 8001 Zurich',
        ST_SetSRID(ST_MakePoint(8.5402, 47.3779), 4326)::geography, NULL, TRUE, 'PUBLIC'::toilet_type,
        'CHF 2, attended', (SELECT id FROM app_user WHERE username='matvs')),
    ('Burger King Limmatquai', 'Limmatquai 80, 8001 Zurich',
        ST_SetSRID(ST_MakePoint(8.5446, 47.3724), 4326)::geography, '4231', TRUE, 'OTHER'::toilet_type,
        'PIN printed on receipt', (SELECT id FROM app_user WHERE username='matvs'))
) AS v(name,address,location,pin_code,is_working,toilet_type,notes,created_by);
