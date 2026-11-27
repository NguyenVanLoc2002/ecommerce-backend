CREATE TABLE tax_categories (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    treatment VARCHAR(15) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    rate_revision INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by CHAR(36),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by CHAR(36),
    CONSTRAINT chk_tax_treatment CHECK (treatment IN ('TAXABLE', 'NON_TAXABLE'))
);

CREATE TABLE tax_rate_schedules (
    category_code VARCHAR(40) NOT NULL,
    revision INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by CHAR(36) NOT NULL,
    PRIMARY KEY (category_code, revision),
    FOREIGN KEY (category_code) REFERENCES tax_categories (code)
);

CREATE TABLE tax_rates (
    category_code VARCHAR(40) NOT NULL,
    revision INT NOT NULL,
    effective_from DATE NOT NULL,
    effective_until DATE,
    fraction DECIMAL(9,8) NOT NULL,
    active BOOLEAN NOT NULL,
    PRIMARY KEY (category_code, revision, effective_from),
    FOREIGN KEY (category_code, revision) REFERENCES tax_rate_schedules (category_code, revision),
    CONSTRAINT chk_tax_rate_interval CHECK (effective_until IS NULL OR effective_until > effective_from),
    CONSTRAINT chk_tax_rate_fraction CHECK (fraction >= 0 AND fraction <= 1)
);

CREATE TABLE product_tax_categories (
    product_id CHAR(36) PRIMARY KEY,
    category_code VARCHAR(40) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products (id),
    FOREIGN KEY (category_code) REFERENCES tax_categories (code)
);

CREATE TABLE tax_configuration_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event VARCHAR(32) NOT NULL,
    subject VARCHAR(40) NOT NULL,
    actor CHAR(36) NOT NULL,
    details JSON NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_tax_audit_subject (subject, id)
);

CREATE TABLE order_tax_snapshots (
    order_id CHAR(36) NOT NULL,
    phase VARCHAR(12) NOT NULL,
    tax_date DATE NOT NULL,
    snapshot JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (order_id, phase),
    FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_order_tax_phase CHECK (phase IN ('ESTIMATE', 'FINAL'))
);

INSERT INTO tax_categories (code, name, treatment) VALUES
    ('STANDARD_REDUCIBLE', 'Standard, eligible for reduction', 'TAXABLE'),
    ('STANDARD_NON_REDUCIBLE', 'Standard, excluded from reduction', 'TAXABLE'),
    ('REDUCED_5', 'Reduced category', 'TAXABLE'),
    ('ZERO_RATE', 'Zero rate', 'TAXABLE'),
    ('NON_TAXABLE', 'Non-taxable', 'NON_TAXABLE');
