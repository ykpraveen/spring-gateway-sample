-- Sequence starts above the small fixed ids used by seed data
-- (db/seed/V2__seed_products.sql) so app-created rows never collide with them.
CREATE SEQUENCE product_id_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE product (
    id BIGINT PRIMARY KEY DEFAULT nextval('product_id_seq'),
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER SEQUENCE product_id_seq OWNED BY product.id;

CREATE UNIQUE INDEX ux_product_sku ON product (sku);
