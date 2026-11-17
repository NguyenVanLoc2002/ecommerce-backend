-- Preserve the ordering customer's identity as it was at checkout.
-- Existing orders deliberately remain NULL; current profile data is not historical evidence.
ALTER TABLE orders
    ADD COLUMN customer_name_snapshot VARCHAR(200) NULL,
    ADD COLUMN customer_email_snapshot VARCHAR(255) NULL;
