-- Normalize legacy payment method values to the current contract.
UPDATE orders
SET payment_method = 'ONLINE'
WHERE payment_method IS NOT NULL
  AND UPPER(payment_method) IN ('MOMO', 'ZALO_PAY', 'ZALOPAY', 'VNPAY', 'BANK_TRANSFER');

UPDATE payments
SET method = 'ONLINE'
WHERE method IS NOT NULL
  AND UPPER(method) IN ('MOMO', 'ZALO_PAY', 'ZALOPAY', 'VNPAY', 'BANK_TRANSFER');

UPDATE payment_transactions
SET method = 'ONLINE'
WHERE method IS NOT NULL
  AND UPPER(method) IN ('MOMO', 'ZALO_PAY', 'ZALOPAY', 'VNPAY', 'BANK_TRANSFER');

-- Normalize provider values so PaymentTransaction.provider can be mapped as an enum.
UPDATE payment_transactions
SET provider = CASE
    WHEN provider IS NULL OR TRIM(provider) = '' THEN NULL
    WHEN UPPER(TRIM(provider)) = 'ZALOPAY' THEN 'ZALO_PAY'
    WHEN UPPER(TRIM(provider)) IN ('COD', 'CASH') THEN NULL
    WHEN UPPER(TRIM(provider)) IN ('MOMO', 'PAYPAL', 'VNPAY', 'ZALO_PAY', 'BANK_TRANSFER', 'MOCK') THEN UPPER(TRIM(provider))
    ELSE 'UNKNOWN'
END;
