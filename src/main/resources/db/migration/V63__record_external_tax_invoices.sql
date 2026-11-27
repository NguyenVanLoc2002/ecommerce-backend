CREATE TABLE external_tax_invoice_records (
    invoice_id CHAR(36) PRIMARY KEY,
    issuer_reference VARCHAR(100) NOT NULL,
    series VARCHAR(40) NOT NULL,
    invoice_number VARCHAR(80) NOT NULL,
    external_reference VARCHAR(160) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    recorded_by CHAR(36) NOT NULL,
    CONSTRAINT fk_external_tax_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT uq_external_tax_invoice UNIQUE (issuer_reference, series, invoice_number)
);
