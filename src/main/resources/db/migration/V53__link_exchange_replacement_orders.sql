ALTER TABLE orders
    ADD COLUMN replacement_for_order_id CHAR(36) NULL,
    ADD CONSTRAINT fk_order_replacement_original FOREIGN KEY (replacement_for_order_id) REFERENCES orders(id);

ALTER TABLE return_exchanges
    ADD COLUMN replacement_order_id CHAR(36) NULL,
    ADD CONSTRAINT fk_exchange_replacement_order FOREIGN KEY (replacement_order_id) REFERENCES orders(id),
    ADD CONSTRAINT uq_exchange_replacement_order UNIQUE (replacement_order_id);
