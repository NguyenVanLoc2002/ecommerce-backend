ALTER TABLE order_returns ADD COLUMN requested_at DATETIME(6) NULL;

CREATE TABLE return_decisions (
    id CHAR(36) PRIMARY KEY,
    return_id CHAR(36) NOT NULL,
    request_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    previous_status VARCHAR(40) NOT NULL,
    next_status VARCHAR(40) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    verified_delivered_at DATETIME(6),
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100),
    CONSTRAINT fk_return_decisions_return FOREIGN KEY (return_id) REFERENCES order_returns(id),
    CONSTRAINT uq_return_decisions_key UNIQUE (return_id, request_key)
);
