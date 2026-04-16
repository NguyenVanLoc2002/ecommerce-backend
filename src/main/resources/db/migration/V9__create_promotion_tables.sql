-- =====================================================
-- V9__create_promotion_tables.sql
-- Phase 7 — Promotion module
-- =====================================================

-- ─── Promotions ───────────────────────────────────────────────────────────────
-- A promotion defines the discount rule (type, value, scope, validity window).
-- Multiple vouchers can reference the same promotion.
-- Supports soft delete so historical records stay queryable.

CREATE TABLE promotions (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name                 VARCHAR(200)   NOT NULL,
    description          TEXT           NULL,
    discount_type        VARCHAR(50)    NOT NULL,               -- PERCENTAGE | FIXED_AMOUNT
    discount_value       DECIMAL(18,2)  NOT NULL,
    max_discount_amount  DECIMAL(18,2)  NULL,                   -- cap for PERCENTAGE type
    minimum_order_amount DECIMAL(18,2)  NULL DEFAULT 0,
    scope                VARCHAR(50)    NOT NULL DEFAULT 'ORDER', -- ORDER | PRODUCT | CATEGORY | BRAND
    start_date           DATETIME       NOT NULL,
    end_date             DATETIME       NOT NULL,
    is_active            TINYINT(1)     NOT NULL DEFAULT 1,
    usage_limit          INT            NULL,                   -- null = unlimited
    usage_count          INT            NOT NULL DEFAULT 0,
    is_deleted           TINYINT(1)     NOT NULL DEFAULT 0,
    deleted_at           DATETIME       NULL,
    deleted_by           VARCHAR(100)   NULL,
    created_at           DATETIME       NOT NULL,
    created_by           VARCHAR(100)   NULL,
    updated_at           DATETIME       NOT NULL,
    updated_by           VARCHAR(100)   NULL
);

CREATE INDEX idx_promotions_active      ON promotions(is_active, is_deleted);
CREATE INDEX idx_promotions_dates       ON promotions(start_date, end_date);
CREATE INDEX idx_promotions_scope       ON promotions(scope);

-- ─── Promotion Rules ──────────────────────────────────────────────────────────
-- Additional conditions that must ALL pass for the discount to apply.
-- Examples:
--   ruleType=MIN_ORDER_AMOUNT  ruleValue="200000"
--   ruleType=SPECIFIC_PRODUCTS ruleValue="1,2,3"       (product IDs)
--   ruleType=SPECIFIC_CATEGORIES ruleValue="5,6"       (category IDs)
--   ruleType=SPECIFIC_BRANDS   ruleValue="2"           (brand IDs)
--   ruleType=FIRST_ORDER       ruleValue="true"

CREATE TABLE promotion_rules (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    promotion_id BIGINT         NOT NULL,
    rule_type    VARCHAR(100)   NOT NULL,
    rule_value   VARCHAR(500)   NOT NULL,
    description  VARCHAR(255)   NULL,
    created_at   DATETIME       NOT NULL,
    created_by   VARCHAR(100)   NULL,
    updated_at   DATETIME       NOT NULL,
    updated_by   VARCHAR(100)   NULL,

    CONSTRAINT fk_rule_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id)
);

CREATE INDEX idx_promo_rules_promotion ON promotion_rules(promotion_id);

-- ─── Vouchers ─────────────────────────────────────────────────────────────────
-- A voucher is a redeemable code that links to a promotion.
-- It can override the promotion's validity window and set per-user limits.
-- Supports soft delete.

CREATE TABLE vouchers (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    code                 VARCHAR(100)   NOT NULL,
    promotion_id         BIGINT         NOT NULL,
    usage_limit          INT            NULL,                   -- null = unlimited
    usage_count          INT            NOT NULL DEFAULT 0,
    usage_limit_per_user INT            NULL,                   -- null = unlimited
    start_date           DATETIME       NOT NULL,
    end_date             DATETIME       NOT NULL,
    is_active            TINYINT(1)     NOT NULL DEFAULT 1,
    is_deleted           TINYINT(1)     NOT NULL DEFAULT 0,
    deleted_at           DATETIME       NULL,
    deleted_by           VARCHAR(100)   NULL,
    created_at           DATETIME       NOT NULL,
    created_by           VARCHAR(100)   NULL,
    updated_at           DATETIME       NOT NULL,
    updated_by           VARCHAR(100)   NULL,

    CONSTRAINT fk_voucher_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id),
    CONSTRAINT uq_voucher_code      UNIQUE (code)
);

CREATE INDEX idx_vouchers_code     ON vouchers(code);
CREATE INDEX idx_vouchers_active   ON vouchers(is_active, is_deleted);
CREATE INDEX idx_vouchers_dates    ON vouchers(start_date, end_date);
CREATE INDEX idx_vouchers_promo    ON vouchers(promotion_id);

-- ─── Voucher Usages ───────────────────────────────────────────────────────────
-- Immutable audit trail of every voucher redemption.
-- order_id is stored as a plain column (not FK) to avoid circular dependency
-- with the orders table.

CREATE TABLE voucher_usages (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    voucher_id      BIGINT         NOT NULL,
    customer_id     BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL,
    discount_amount DECIMAL(18,2)  NOT NULL,
    created_at      DATETIME       NOT NULL,
    created_by      VARCHAR(100)   NULL,
    updated_at      DATETIME       NOT NULL,
    updated_by      VARCHAR(100)   NULL,

    CONSTRAINT fk_usage_voucher  FOREIGN KEY (voucher_id)  REFERENCES vouchers(id),
    CONSTRAINT fk_usage_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_voucher_usages_voucher   ON voucher_usages(voucher_id);
CREATE INDEX idx_voucher_usages_customer  ON voucher_usages(customer_id);
CREATE INDEX idx_voucher_usages_order     ON voucher_usages(order_id);
