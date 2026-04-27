-- =====================================================
-- V11__create_engagement_tables.sql
-- Phase 9 — Engagement module (Review + Notification)
-- =====================================================

-- ─── Reviews ──────────────────────────────────────────────────────────────────
-- Verified-purchase reviews only: each row is anchored to an order_item to prove
-- the customer actually bought and received the product (order must be COMPLETED).
-- Unique constraint on (customer_id, product_id) enforces one review per product.
-- Extends BaseEntity (permanent record — no soft delete).

CREATE TABLE reviews (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id   BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    order_item_id BIGINT         NOT NULL,
    rating        TINYINT        NOT NULL,           -- 1 – 5
    title         VARCHAR(255)   NULL,
    body          TEXT           NULL,
    status        VARCHAR(50)    NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    admin_note    VARCHAR(500)   NULL,               -- moderation note (internal)
    created_at    DATETIME       NOT NULL,
    created_by    VARCHAR(100)   NULL,
    updated_at    DATETIME       NOT NULL,
    updated_by    VARCHAR(100)   NULL,

    CONSTRAINT fk_review_customer   FOREIGN KEY (customer_id)   REFERENCES customers(id),
    CONSTRAINT fk_review_product    FOREIGN KEY (product_id)    REFERENCES products(id),
    CONSTRAINT fk_review_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),

    -- One review per customer per product
    CONSTRAINT uq_review_customer_product UNIQUE (customer_id, product_id)
);

CREATE INDEX idx_reviews_product    ON reviews(product_id);
CREATE INDEX idx_reviews_customer   ON reviews(customer_id);
CREATE INDEX idx_reviews_status     ON reviews(status);
CREATE INDEX idx_reviews_rating     ON reviews(rating);
CREATE INDEX idx_reviews_created_at ON reviews(created_at);

-- ─── Notifications ────────────────────────────────────────────────────────────
-- In-app notification inbox per customer.
-- reference_type + reference_id form a polymorphic link to the triggering entity
-- (e.g. type=ORDER, id=42 — no hard FK so the table stays decoupled).
-- Extends BaseEntity (permanent record — never deleted, only marked read).

CREATE TABLE notifications (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id    BIGINT         NOT NULL,
    type           VARCHAR(100)   NOT NULL,          -- NotificationType enum
    title          VARCHAR(255)   NOT NULL,
    message        TEXT           NOT NULL,
    reference_type VARCHAR(50)    NULL,              -- e.g. ORDER, REVIEW, PAYMENT
    reference_id   VARCHAR(100)   NULL,              -- entity ID / code
    is_read        TINYINT(1)     NOT NULL DEFAULT 0,
    read_at        DATETIME       NULL,
    created_at     DATETIME       NOT NULL,
    created_by     VARCHAR(100)   NULL,
    updated_at     DATETIME       NOT NULL,
    updated_by     VARCHAR(100)   NULL,

    CONSTRAINT fk_notification_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_notifications_customer        ON notifications(customer_id);
CREATE INDEX idx_notifications_customer_unread ON notifications(customer_id, is_read);
CREATE INDEX idx_notifications_type            ON notifications(type);
CREATE INDEX idx_notifications_created_at      ON notifications(created_at);
