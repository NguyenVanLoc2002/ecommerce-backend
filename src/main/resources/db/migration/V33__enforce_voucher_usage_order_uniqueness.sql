-- A checkout order may redeem a voucher at most once. This protects idempotent
-- retries and concurrent redemption attempts at the database boundary.
ALTER TABLE voucher_usages
    ADD CONSTRAINT uk_voucher_usages_voucher_order UNIQUE (voucher_id, order_id);
