-- Sequence starts above small fixed ids any future seed data would use.
CREATE SEQUENCE api_client_id_seq START WITH 1000 INCREMENT BY 1;

-- One row per API client. key_hash is the HMAC-SHA256 (hex-encoded) digest of
-- the client's current raw API key, computed with a server-side pepper; the
-- raw key itself is never persisted. The unique index gives validation a
-- direct indexed lookup by digest instead of a per-row salted comparison.
CREATE TABLE api_client (
    id BIGINT PRIMARY KEY DEFAULT nextval('api_client_id_seq'),
    name VARCHAR(255) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    tier VARCHAR(32) NOT NULL DEFAULT 'standard',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER SEQUENCE api_client_id_seq OWNED BY api_client.id;

CREATE UNIQUE INDEX ux_api_client_name ON api_client (name);
CREATE UNIQUE INDEX ux_api_client_key_hash ON api_client (key_hash);
