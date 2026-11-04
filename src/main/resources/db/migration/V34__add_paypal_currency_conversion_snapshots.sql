-- Canonical payment values remain VND; provider values are immutable reconciliation snapshots.
ALTER TABLE payments
    ADD COLUMN provider_amount DECIMAL(18,2) NULL AFTER amount,
    ADD COLUMN provider_currency VARCHAR(3) NULL AFTER provider_amount,
    ADD COLUMN provider_exchange_rate DECIMAL(18,6) NULL AFTER provider_currency;

ALTER TABLE payment_transactions
    ADD COLUMN provider_amount DECIMAL(18,2) NULL AFTER amount,
    ADD COLUMN provider_currency VARCHAR(3) NULL AFTER provider_amount,
    ADD COLUMN provider_exchange_rate DECIMAL(18,6) NULL AFTER provider_currency;

ALTER TABLE payment_configs
    ADD COLUMN paypal_conversion_rate_vnd_usd DECIMAL(18,6) NULL AFTER paypal_test_conversion_rate_vnd_usd;

UPDATE payment_configs
SET paypal_conversion_rate_vnd_usd = paypal_test_conversion_rate_vnd_usd
WHERE paypal_conversion_rate_vnd_usd IS NULL
  AND paypal_test_conversion_rate_vnd_usd IS NOT NULL;
