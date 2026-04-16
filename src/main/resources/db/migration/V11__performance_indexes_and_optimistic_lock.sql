-- =====================================================
-- V11__performance_indexes_and_optimistic_lock.sql
-- Production hardening:
--   1. Optimistic-lock version columns on vouchers + promotions
--   2. Composite index on voucher_usages(voucher_id, customer_id)
--      — speeds up per-user usage-limit queries
--   3. Composite index on orders(customer_id, status)
--      — speeds up "my orders filtered by status" queries
--   4. Index on inventory_reservations(reference_type, reference_id)
--      — speeds up release/complete-order lookups
--   5. Index on payment_transactions(provider_txn_id)
--      — speeds up idempotency check in payment callback
--   6. Index on orders(order_code)
--      — speeds up payment-callback → order lookup
-- =====================================================

-- ─── Optimistic lock columns ──────────────────────────────────────────────────

ALTER TABLE vouchers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE promotions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ─── voucher_usages ──────────────────────────────────────────────────────────

-- Per-user limit query: WHERE voucher_id = ? AND customer_id = ?
CREATE INDEX idx_voucher_usages_voucher_customer
    ON voucher_usages(voucher_id, customer_id);

-- ─── orders ──────────────────────────────────────────────────────────────────

-- "My orders" list filtered by status: WHERE customer_id = ? AND status = ?
CREATE INDEX idx_orders_customer_status
    ON orders(customer_id, status);

-- Payment-callback order lookup: WHERE order_code = ?
-- (order_code already has a UNIQUE constraint which implies an index,
--  but an explicit named index makes intent visible in EXPLAIN plans)
-- Skip if your DDL already created one — Flyway will error; remove if so.
CREATE INDEX idx_orders_order_code
    ON orders(order_code);

-- ─── inventory_reservations ──────────────────────────────────────────────────

-- Release/complete-order reservation lookup:
-- WHERE reference_type = 'ORDER' AND reference_id = ?
CREATE INDEX idx_inv_reservations_reference
    ON inventory_reservations(reference_type, reference_id);

-- ─── payment_transactions ────────────────────────────────────────────────────

-- Idempotency check in processCallback: WHERE provider_txn_id = ?
CREATE INDEX idx_payment_txn_provider_txn_id
    ON payment_transactions(provider_txn_id);
