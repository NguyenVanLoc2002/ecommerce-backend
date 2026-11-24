CREATE TABLE order_returns (
    id CHAR(36) PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    submitted_by_customer_id CHAR(36) NOT NULL,
    request_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    reason VARCHAR(30) NOT NULL,
    customer_note VARCHAR(1000) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100),
    CONSTRAINT fk_order_returns_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_returns_submitter FOREIGN KEY (submitted_by_customer_id) REFERENCES customers(id),
    CONSTRAINT uq_order_returns_key UNIQUE (order_id, request_key)
);

CREATE TABLE order_return_lines (
    return_id CHAR(36) NOT NULL,
    order_item_id CHAR(36) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (return_id, order_item_id),
    CONSTRAINT fk_return_lines_return FOREIGN KEY (return_id) REFERENCES order_returns(id),
    CONSTRAINT fk_return_lines_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT ck_order_return_line_quantity CHECK (quantity > 0)
);
