-- =====================================================
-- V27__add_ahamove_provider_support.sql
-- Provider tracking fields and carrier webhook logs
-- =====================================================

ALTER TABLE shipments
    ADD COLUMN provider_status VARCHAR(100) NULL,
    ADD COLUMN provider_tracking_url VARCHAR(500) NULL;

CREATE INDEX idx_shipments_tracking_number ON shipments(tracking_number);
CREATE INDEX idx_shipments_provider_status ON shipments(provider_status);

CREATE TABLE carrier_webhook_logs (
    id                 CHAR(36)      NOT NULL PRIMARY KEY,
    shipment_id        CHAR(36)      NULL,
    carrier_code       VARCHAR(100)  NOT NULL,
    provider_order_id  VARCHAR(200)  NULL,
    tracking_number    VARCHAR(200)  NULL,
    event_type         VARCHAR(100)  NOT NULL,
    event_key          VARCHAR(128)  NOT NULL,
    payload            TEXT          NULL,
    headers            TEXT          NULL,
    signature_valid    BOOLEAN       NULL,
    processed          BOOLEAN       NOT NULL DEFAULT FALSE,
    processed_at       DATETIME(6)   NULL,
    error_message      VARCHAR(500)  NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    CONSTRAINT fk_carrier_webhook_logs_shipment
        FOREIGN KEY (shipment_id) REFERENCES shipments(id),
    CONSTRAINT uq_carrier_webhook_logs_event_key UNIQUE (event_key)
);

CREATE INDEX idx_carrier_webhook_logs_carrier_code ON carrier_webhook_logs(carrier_code);
CREATE INDEX idx_carrier_webhook_logs_provider_order_id ON carrier_webhook_logs(provider_order_id);
CREATE INDEX idx_carrier_webhook_logs_tracking_number ON carrier_webhook_logs(tracking_number);
