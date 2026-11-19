CREATE TABLE shipment_booking_attempts (
    id CHAR(36) NOT NULL,
    shipment_id CHAR(36) NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    last_error_code VARCHAR(100) NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_shipment_booking_attempts PRIMARY KEY (id),
    CONSTRAINT uq_booking_attempt_shipment UNIQUE (shipment_id),
    CONSTRAINT uq_booking_attempt_key UNIQUE (idempotency_key),
    CONSTRAINT fk_booking_attempt_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id)
);

CREATE INDEX idx_booking_attempt_status ON shipment_booking_attempts(status, updated_at);

ALTER TABLE shipments
    ADD COLUMN booking_status VARCHAR(30) NULL,
    ADD COLUMN booking_attempt_key VARCHAR(100) NULL,
    ADD COLUMN carrier_cost DECIMAL(18,2) NULL,
    ADD COLUMN estimated_carrier_cost DECIMAL(18,2) NULL,
    ADD COLUMN override_reason_code VARCHAR(50) NULL,
    ADD COLUMN override_note VARCHAR(500) NULL,
    ADD COLUMN override_actor VARCHAR(100) NULL,
    ADD COLUMN override_from_carrier_id CHAR(36) NULL,
    ADD COLUMN override_to_carrier_id CHAR(36) NULL,
    ADD COLUMN override_at DATETIME NULL;
