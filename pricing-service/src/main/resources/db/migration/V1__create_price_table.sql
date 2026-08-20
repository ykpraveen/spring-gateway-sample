-- Sequence starts above the small fixed ids used by seed data
-- (db/seed/V2__seed_prices.sql) so app-created rows never collide with them.
CREATE SEQUENCE price_id_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE price (
    id BIGINT PRIMARY KEY DEFAULT nextval('price_id_seq'),
    product_id BIGINT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER SEQUENCE price_id_seq OWNED BY price.id;

CREATE INDEX ix_price_product_id ON price (product_id);

-- Enforces "at most one active price per product" at the database level,
-- independent of the service-layer supersede-then-insert logic.
CREATE UNIQUE INDEX ux_price_active_product ON price (product_id) WHERE active;
