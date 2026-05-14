-- =====================================================
-- V25__add_provider_fields_to_payments.sql
-- Phase 4 — MoMo payment provider integration
--
-- Adds provider_order_id and provider_request_id to the
-- payments table so the MoMo orderId can be stored at
-- initiate time and used for IPN callback mapping in Session 2.
-- =====================================================

ALTER TABLE payments
    ADD COLUMN provider_order_id   VARCHAR(100) NULL AFTER expired_at,
    ADD COLUMN provider_request_id VARCHAR(100) NULL AFTER provider_order_id;

-- Index to support fast lookup by MoMo orderId during IPN processing
CREATE INDEX idx_payments_provider_order_id ON payments (provider_order_id);
