-- Fixed small ids (well below the product_id_seq start value of 1000) so
-- pricing-service's own seed data can reference the same product ids across
-- the two independently seeded services.
INSERT INTO product (id, sku, name, description, active) VALUES
    (1, 'DESK-LAMP-001', 'Desk Lamp', 'Adjustable LED desk lamp', true),
    (2, 'KEYBOARD-002', 'Mechanical Keyboard', 'Tactile mechanical keyboard, US layout', true),
    (3, 'MONITOR-003', '27-inch Monitor', '27-inch 4K IPS monitor', true),
    (4, 'CHAIR-004', 'Ergonomic Chair', 'Adjustable ergonomic office chair', true),
    (5, 'WEBCAM-005', 'HD Webcam', '1080p USB webcam with autofocus', false);
