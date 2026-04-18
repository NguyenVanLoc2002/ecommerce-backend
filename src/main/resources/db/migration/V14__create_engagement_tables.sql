-- =====================================================
-- V14__create_engagement_tables.sql
-- Phase 9 — Engagement module (Review + Notification)
-- =====================================================

-- ─── Reviews ──────────────────────────────────────────────────────────────────
-- One review per order item (unique constraint on order_item_id).
-- Customers may only review products from COMPLETED orders.
-- Reviews are PENDING until moderated (APPROVED / REJECTED) by staff/admin.
-- Extends SoftDeleteEntity — reviews can be soft-deleted by admin.

CREATE TABLE reviews (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT          NOT NULL,
    product_id      BIGINT          NOT NULL,
    variant_id      BIGINT          NOT NULL,
    order_item_id   BIGINT          NOT NULL,               -- source order item (eligibility + uniqueness)
    rating          TINYINT         NOT NULL,               -- 1–5
    comment         TEXT            NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    admin_note      VARCHAR(500)    NULL,
    moderated_at    DATETIME        NULL,
    moderated_by    VARCHAR(100)    NULL,

    -- BaseEntity audit fields
    created_at      DATETIME        NOT NULL,
    created_by      VARCHAR(100)    NULL,
    updated_at      DATETIME        NOT NULL,
    updated_by      VARCHAR(100)    NULL,

    -- SoftDeleteEntity fields
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      DATETIME        NULL,
    deleted_by      VARCHAR(100)    NULL,

    CONSTRAINT uc_review_order_item  UNIQUE  (order_item_id),
    CONSTRAINT fk_review_customer    FOREIGN KEY (customer_id)   REFERENCES customers(id),
    CONSTRAINT fk_review_product     FOREIGN KEY (product_id)    REFERENCES products(id),
    CONSTRAINT fk_review_variant     FOREIGN KEY (variant_id)    REFERENCES product_variants(id),
    CONSTRAINT fk_review_order_item  FOREIGN KEY (order_item_id) REFERENCES order_items(id)
);

CREATE INDEX idx_reviews_customer        ON reviews(customer_id);
CREATE INDEX idx_reviews_product         ON reviews(product_id);
CREATE INDEX idx_reviews_variant         ON reviews(variant_id);
CREATE INDEX idx_reviews_status          ON reviews(status);
CREATE INDEX idx_reviews_product_status  ON reviews(product_id, status);
CREATE INDEX idx_reviews_created_at      ON reviews(created_at);

-- ─── Notifications ────────────────────────────────────────────────────────────
-- In-app notification feed per customer.
-- Extends BaseEntity (permanent records — no soft delete).
-- reference_id / reference_type link to the related domain entity (ORDER, REVIEW …).

CREATE TABLE notifications (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT          NOT NULL,
    type            VARCHAR(50)     NOT NULL,               -- NotificationType enum
    title           VARCHAR(255)    NOT NULL,
    body            TEXT            NOT NULL,
    reference_id    BIGINT          NULL,                   -- optional FK to related entity PK
    reference_type  VARCHAR(50)     NULL,                   -- e.g. "ORDER", "REVIEW"
    is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
    read_at         DATETIME        NULL,

    -- BaseEntity audit fields
    created_at      DATETIME        NOT NULL,
    created_by      VARCHAR(100)    NULL,
    updated_at      DATETIME        NOT NULL,
    updated_by      VARCHAR(100)    NULL,

    CONSTRAINT fk_notification_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_notifications_customer         ON notifications(customer_id);
CREATE INDEX idx_notifications_customer_unread  ON notifications(customer_id, is_read);
CREATE INDEX idx_notifications_created_at       ON notifications(created_at);
