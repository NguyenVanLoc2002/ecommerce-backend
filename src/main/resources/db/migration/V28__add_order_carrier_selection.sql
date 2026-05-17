ALTER TABLE orders
    ADD COLUMN carrier_id CHAR(36) NULL,
    ADD COLUMN carrier_code VARCHAR(50) NULL,
    ADD COLUMN carrier_name VARCHAR(200) NULL,
    ADD COLUMN carrier_provider_type VARCHAR(100) NULL;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_carrier
        FOREIGN KEY (carrier_id) REFERENCES carriers(id);

CREATE INDEX idx_orders_carrier_id ON orders(carrier_id);
