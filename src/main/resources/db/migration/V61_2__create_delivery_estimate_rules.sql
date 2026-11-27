CREATE TABLE delivery_estimate_rules (
    id CHAR(36) PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 1,
    carrier_id CHAR(36) NOT NULL,
    service_code VARCHAR(100) NOT NULL,
    warehouse_id CHAR(36) NOT NULL,
    province_code CHAR(2) NOT NULL,
    ward_code CHAR(5),
    calendar_id CHAR(36) NOT NULL,
    min_business_days SMALLINT NOT NULL,
    max_business_days SMALLINT NOT NULL,
    priority INT NOT NULL,
    effective_from DATE NOT NULL,
    effective_until DATE,
    active BOOLEAN NOT NULL,
    guaranteed BOOLEAN NOT NULL,
    coverage_reference VARCHAR(200),
    CONSTRAINT fk_delivery_estimate_carrier FOREIGN KEY (carrier_id) REFERENCES carriers (id),
    CONSTRAINT fk_delivery_estimate_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_delivery_estimate_province FOREIGN KEY (province_code) REFERENCES address_provinces (code),
    CONSTRAINT fk_delivery_estimate_ward FOREIGN KEY (ward_code) REFERENCES address_wards (code),
    CONSTRAINT fk_delivery_estimate_calendar FOREIGN KEY (calendar_id) REFERENCES shipping_calendars (id),
    CONSTRAINT chk_delivery_day_range CHECK (min_business_days >= 0 AND max_business_days >= min_business_days AND max_business_days <= 90),
    CONSTRAINT chk_delivery_effectivity CHECK (effective_until IS NULL OR effective_until > effective_from),
    INDEX idx_delivery_estimate_match (carrier_id, warehouse_id, service_code, province_code, active, ward_code, priority, effective_from)
);

CREATE TABLE delivery_estimate_rule_revisions (
    rule_id CHAR(36) NOT NULL,
    revision BIGINT NOT NULL,
    changed_by CHAR(36) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    snapshot JSON NOT NULL,
    PRIMARY KEY (rule_id, revision),
    FOREIGN KEY (rule_id) REFERENCES delivery_estimate_rules (id)
);
