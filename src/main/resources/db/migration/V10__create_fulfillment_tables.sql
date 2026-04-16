-- =====================================================
-- V10__create_fulfillment_tables.sql
-- Phase 8 — Fulfillment module (Shipment + Invoice)
-- =====================================================

-- ─── Shipments ────────────────────────────────────────────────────────────────
-- One shipment per order (MVP). Tracks carrier, tracking number and delivery status.
-- Extends BaseEntity (permanent record — no soft delete).

CREATE TABLE shipments (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id                BIGINT         NOT NULL,
    shipment_code           VARCHAR(50)    NOT NULL,
    carrier                 VARCHAR(100)   NOT NULL,          -- e.g. GHTK, GHN, VNPOST, J&T
    tracking_number         VARCHAR(200)   NULL,              -- carrier's own reference
    status                  VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    estimated_delivery_date DATE           NULL,
    delivered_at            DATETIME       NULL,
    shipping_fee            DECIMAL(18,2)  NOT NULL DEFAULT 0,
    note                    VARCHAR(500)   NULL,
    created_at              DATETIME       NOT NULL,
    created_by              VARCHAR(100)   NULL,
    updated_at              DATETIME       NOT NULL,
    updated_by              VARCHAR(100)   NULL,

    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT uq_shipment_order UNIQUE (order_id),
    CONSTRAINT uq_shipment_code  UNIQUE (shipment_code)
);

CREATE INDEX idx_shipments_status       ON shipments(status);
CREATE INDEX idx_shipments_carrier      ON shipments(carrier);
CREATE INDEX idx_shipments_created_at   ON shipments(created_at);
CREATE INDEX idx_shipments_tracking     ON shipments(tracking_number);

-- ─── Shipment Events ──────────────────────────────────────────────────────────
-- Immutable tracking history — one row per status change or scan event.
-- Ordered by event_time for the tracking timeline.

CREATE TABLE shipment_events (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    shipment_id  BIGINT         NOT NULL,
    status       VARCHAR(50)    NOT NULL,              -- ShipmentStatus at this point
    location     VARCHAR(255)   NULL,                  -- e.g. "Ho Chi Minh City hub"
    description  VARCHAR(500)   NOT NULL,
    event_time   DATETIME       NOT NULL,
    created_at   DATETIME       NOT NULL,
    created_by   VARCHAR(100)   NULL,
    updated_at   DATETIME       NOT NULL,
    updated_by   VARCHAR(100)   NULL,

    CONSTRAINT fk_event_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id)
);

CREATE INDEX idx_shipment_events_shipment ON shipment_events(shipment_id);
CREATE INDEX idx_shipment_events_time     ON shipment_events(event_time);

-- ─── Invoices ─────────────────────────────────────────────────────────────────
-- One invoice per order. All pricing figures are denormalized from the order at
-- generation time so the invoice is self-contained and audit-safe.
-- Extends BaseEntity (permanent record — no soft delete).

CREATE TABLE invoices (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id          BIGINT         NOT NULL,
    invoice_code      VARCHAR(50)    NOT NULL,
    status            VARCHAR(50)    NOT NULL DEFAULT 'ISSUED',

    -- Customer snapshot at invoice generation time
    customer_name     VARCHAR(200)   NOT NULL,
    customer_email    VARCHAR(255)   NOT NULL,
    customer_phone    VARCHAR(20)    NULL,

    -- Billing address snapshot (from order's shipping address)
    billing_street    VARCHAR(255)   NOT NULL,
    billing_ward      VARCHAR(100)   NOT NULL,
    billing_district  VARCHAR(100)   NOT NULL,
    billing_city      VARCHAR(100)   NOT NULL,
    billing_postal_code VARCHAR(20)  NULL,

    -- Amounts snapshot
    sub_total         DECIMAL(18,2)  NOT NULL,
    discount_amount   DECIMAL(18,2)  NOT NULL DEFAULT 0,
    shipping_fee      DECIMAL(18,2)  NOT NULL DEFAULT 0,
    total_amount      DECIMAL(18,2)  NOT NULL,
    voucher_code      VARCHAR(100)   NULL,

    -- Dates
    issued_at         DATETIME       NOT NULL,
    due_date          DATE           NULL,

    notes             VARCHAR(1000)  NULL,
    created_at        DATETIME       NOT NULL,
    created_by        VARCHAR(100)   NULL,
    updated_at        DATETIME       NOT NULL,
    updated_by        VARCHAR(100)   NULL,

    CONSTRAINT fk_invoice_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT uq_invoice_order UNIQUE (order_id),
    CONSTRAINT uq_invoice_code  UNIQUE (invoice_code)
);

CREATE INDEX idx_invoices_status     ON invoices(status);
CREATE INDEX idx_invoices_issued_at  ON invoices(issued_at);
