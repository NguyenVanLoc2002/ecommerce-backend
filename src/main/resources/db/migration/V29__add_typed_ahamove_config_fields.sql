ALTER TABLE store_carrier_configs
    ADD COLUMN provider_account_id VARCHAR(100) NULL,
    ADD COLUMN provider_account_phone VARCHAR(50) NULL,
    ADD COLUMN provider_brand_name VARCHAR(200) NULL,
    ADD COLUMN pickup_address VARCHAR(500) NULL,
    ADD COLUMN pickup_short_address VARCHAR(255) NULL,
    ADD COLUMN pickup_name VARCHAR(200) NULL,
    ADD COLUMN pickup_phone VARCHAR(50) NULL,
    ADD COLUMN pickup_lat DECIMAL(10,7) NULL,
    ADD COLUMN pickup_lng DECIMAL(10,7) NULL,
    ADD COLUMN default_service_code VARCHAR(100) NULL,
    ADD COLUMN default_payment_method VARCHAR(50) NULL,
    ADD COLUMN connection_status VARCHAR(50) NULL,
    ADD COLUMN last_health_check_at DATETIME(6) NULL,
    ADD COLUMN last_health_check_error VARCHAR(1000) NULL;

CREATE INDEX idx_scc_connection_status ON store_carrier_configs(connection_status);
