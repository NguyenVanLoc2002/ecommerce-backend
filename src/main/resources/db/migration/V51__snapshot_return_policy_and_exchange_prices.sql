ALTER TABLE products ADD COLUMN return_policy VARCHAR(32) NULL;
ALTER TABLE categories ADD COLUMN return_policy VARCHAR(32) NULL;
ALTER TABLE order_items
    ADD COLUMN return_policy_snapshot VARCHAR(32) NULL,
    ADD COLUMN exchange_base_prices JSON NULL;
ALTER TABLE order_returns
    ADD COLUMN original_condition BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN unopened BOOLEAN NOT NULL DEFAULT FALSE;
