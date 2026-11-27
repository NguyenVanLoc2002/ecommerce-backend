ALTER TABLE orders
    ADD COLUMN delivery_sla_service_code VARCHAR(100) NULL,
    ADD COLUMN delivery_sla_min_business_days INT NULL,
    ADD COLUMN delivery_sla_max_business_days INT NULL;
