ALTER TABLE payment_refunds
    ADD COLUMN return_id CHAR(36) NULL,
    ADD COLUMN return_line_allocations JSON NULL,
    ADD COLUMN return_shipping_amount DECIMAL(18, 2) NULL,
    ADD CONSTRAINT fk_payment_refund_return FOREIGN KEY (return_id) REFERENCES order_returns(id),
    ADD CONSTRAINT uq_payment_refund_return UNIQUE (return_id);
