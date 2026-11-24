CREATE TABLE return_handling (
    id CHAR(36) PRIMARY KEY,
    return_id CHAR(36) NOT NULL,
    request_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    next_status VARCHAR(40) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    carrier VARCHAR(100),
    tracking_number VARCHAR(200),
    tracking_at DATETIME(6),
    evidence_reference VARCHAR(500),
    warehouse_id CHAR(36),
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100),
    CONSTRAINT fk_return_handling_return FOREIGN KEY (return_id) REFERENCES order_returns(id),
    CONSTRAINT fk_return_handling_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT uq_return_handling_status UNIQUE (return_id, next_status),
    CONSTRAINT uq_return_handling_key UNIQUE (return_id, request_key)
);

CREATE TABLE return_handling_lines (
    handling_id CHAR(36) NOT NULL,
    order_item_id CHAR(36) NOT NULL,
    quantity INT NOT NULL,
    restock INT NOT NULL,
    damaged INT NOT NULL,
    PRIMARY KEY (handling_id, order_item_id),
    CONSTRAINT fk_return_handling_lines_operation FOREIGN KEY (handling_id) REFERENCES return_handling(id),
    CONSTRAINT fk_return_handling_lines_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT ck_return_handling_quantities CHECK (quantity > 0 AND restock >= 0 AND damaged >= 0)
);
