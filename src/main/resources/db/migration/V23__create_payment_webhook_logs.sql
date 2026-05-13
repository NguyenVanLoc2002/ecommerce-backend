-- =====================================================
-- V23__create_payment_webhook_logs.sql
-- Phase 3 — Payment Webhook Audit Log
-- =====================================================
-- Stores every inbound gateway callback for audit, replay,
-- and debugging. payment_id is nullable because the log
-- may be written before the matching Payment is resolved.

CREATE TABLE payment_webhook_logs (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    payment_id      CHAR(36)        NULL,
    provider        VARCHAR(100)    NOT NULL,
    order_code      VARCHAR(50)     NULL,
    provider_txn_id VARCHAR(200)    NULL,
    payload         TEXT            NULL,
    signature       VARCHAR(500)    NULL,
    signature_valid BOOLEAN         NULL,
    status          VARCHAR(50)     NOT NULL DEFAULT 'RECEIVED',
    processed_at    DATETIME        NULL,
    error_message   VARCHAR(500)    NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_webhook_log_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_webhook_log_provider        ON payment_webhook_logs(provider);
CREATE INDEX idx_webhook_log_order_code      ON payment_webhook_logs(order_code);
CREATE INDEX idx_webhook_log_provider_txn_id ON payment_webhook_logs(provider_txn_id);
CREATE INDEX idx_webhook_log_status          ON payment_webhook_logs(status);
CREATE INDEX idx_webhook_log_created_at      ON payment_webhook_logs(created_at);
