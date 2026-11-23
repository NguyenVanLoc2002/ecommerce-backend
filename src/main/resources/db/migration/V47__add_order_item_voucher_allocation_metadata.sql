ALTER TABLE order_items
    ADD COLUMN voucher_eligible BOOLEAN NULL,
    ADD COLUMN voucher_allocation_version INT NULL,
    ADD COLUMN net_line_amount DECIMAL(18, 2) NULL;
