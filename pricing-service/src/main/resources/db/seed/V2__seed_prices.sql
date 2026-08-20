-- product_id values are the same fixed small ids used by product-service's
-- own seed data (db/seed/V2__seed_products.sql), so the two independently
-- seeded services present consistent demo data.
INSERT INTO price (id, product_id, amount, currency, active) VALUES
    (1, 1, 29.99, 'EUR', true),
    (2, 2, 89.00, 'EUR', true),
    (3, 3, 349.50, 'EUR', true),
    (4, 4, 219.00, 'EUR', true);
