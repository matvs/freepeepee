-- V1__init.sql : freepeepee initial schema
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username      VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_enabled    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TYPE toilet_type AS ENUM ('MCDONALDS', 'GAS_STATION', 'PARK', 'CAFE', 'PUBLIC', 'OTHER');

CREATE TABLE toilet (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name         VARCHAR(120) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    location     GEOGRAPHY(POINT, 4326) NOT NULL,
    pin_code     VARCHAR(32),
    is_working   BOOLEAN NOT NULL DEFAULT TRUE,
    toilet_type  toilet_type NOT NULL DEFAULT 'OTHER',
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID REFERENCES app_user(id),
    version      BIGINT NOT NULL DEFAULT 0
);

-- GIST index for fast radius queries (ST_DWithin)
CREATE INDEX idx_toilet_location ON toilet USING GIST (location);
CREATE INDEX idx_toilet_type ON toilet (toilet_type);
CREATE INDEX idx_toilet_working ON toilet (is_working);

-- Full text search on name + notes
CREATE INDEX idx_toilet_search ON toilet USING GIN (
    to_tsvector('simple', coalesce(name,'') || ' ' || coalesce(notes,'') || ' ' || coalesce(address,''))
);

-- Audit log : append-only, never UPDATE or DELETE
CREATE TABLE audit_entry (
    id            BIGSERIAL PRIMARY KEY,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id      UUID REFERENCES app_user(id),
    actor_name    VARCHAR(64) NOT NULL,
    actor_ip      INET,
    actor_agent   VARCHAR(512),
    entity_type   VARCHAR(64) NOT NULL,
    entity_id     UUID,
    operation     VARCHAR(16) NOT NULL,    -- CREATE | UPDATE | DELETE | LOGIN | LOGIN_FAIL
    field_name    VARCHAR(64),             -- null for whole-entity ops
    old_value     TEXT,
    new_value     TEXT,
    request_id    UUID                     -- groups field-level changes from one request
);
CREATE INDEX idx_audit_entity ON audit_entry (entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_entry (actor_id);
CREATE INDEX idx_audit_when ON audit_entry (occurred_at DESC);

-- Defensive trigger : audit_entry is append-only
CREATE OR REPLACE FUNCTION audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_entry is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update BEFORE UPDATE OR DELETE ON audit_entry
    FOR EACH ROW EXECUTE FUNCTION audit_immutable();
