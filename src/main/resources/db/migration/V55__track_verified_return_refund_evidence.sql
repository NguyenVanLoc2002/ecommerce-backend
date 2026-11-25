ALTER TABLE payment_refunds
    ADD COLUMN settlement_reference VARCHAR(200) NULL,
    ADD COLUMN settlement_submitted_by VARCHAR(255) NULL,
    ADD CONSTRAINT uq_payment_refunds_settlement_reference UNIQUE (settlement_reference);
