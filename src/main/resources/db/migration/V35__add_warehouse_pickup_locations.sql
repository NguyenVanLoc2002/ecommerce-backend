-- Managed warehouse pickup profiles for carrier-provider shipment requests.

ALTER TABLE warehouses
    ADD COLUMN pickup_address VARCHAR(500) NULL,
    ADD COLUMN pickup_short_address VARCHAR(255) NULL,
    ADD COLUMN pickup_contact_name VARCHAR(200) NULL,
    ADD COLUMN pickup_contact_phone VARCHAR(50) NULL,
    ADD COLUMN pickup_lat DECIMAL(10, 7) NULL,
    ADD COLUMN pickup_lng DECIMAL(10, 7) NULL;

ALTER TABLE store_carrier_configs
    ADD COLUMN default_pickup_warehouse_id CHAR(36) NULL,
    ADD CONSTRAINT fk_scc_default_pickup_warehouse
        FOREIGN KEY (default_pickup_warehouse_id) REFERENCES warehouses(id);

CREATE INDEX idx_scc_default_pickup_warehouse
    ON store_carrier_configs (default_pickup_warehouse_id);
