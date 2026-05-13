-- =====================================================
-- V24__create_payment_refunds.sql
-- Phase 3 — Payment Refund Records
-- =====================================================
-- One row per refund request. A single Payment may have
-- multiple partial refunds until total refunded equals amount.
-- Full audit trail via created_by / updated_by.

CREATE TABLE payment_refunds (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    payment_id          CHAR(36)        NOT NULL,
    refund_code         VARCHAR(50)     NOT NULL,
    amount              DECIMAL(18,2)   NOT NULL,
    reason              VARCHAR(500)    NULL,
    status              VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    provider_refund_id  VARCHAR(200)    NULL,
    refunded_at         DATETIME        NULL,
    requested_by        VARCHAR(255)    NULL,
    note                VARCHAR(500)    NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by          VARCHAR(255)    NULL,
    updated_by          VARCHAR(255)    NULL,
    CONSTRAINT uq_payment_refunds_code UNIQUE (refund_code),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_payment_refunds_payment_id ON payment_refunds(payment_id);
CREATE INDEX idx_payment_refunds_status     ON payment_refunds(status);
CREATE INDEX idx_payment_refunds_created_at ON payment_refunds(created_at);
