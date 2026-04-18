-- ─── V13: Add warehouse_id to inventory_reservations ────────────────────────
-- Previously the warehouse had to be recovered by scanning stock_movements,
-- which was fragile and added extra queries. Storing it directly on the
-- reservation makes release/complete operations O(1) for the warehouse lookup.

ALTER TABLE inventory_reservations
    ADD COLUMN warehouse_id BIGINT NULL AFTER variant_id,
    ADD CONSTRAINT fk_inv_reservation_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

CREATE INDEX idx_inv_reservation_warehouse ON inventory_reservations (warehouse_id);
