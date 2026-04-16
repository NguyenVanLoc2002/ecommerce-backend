package com.locnguyen.ecommerce.domains.auditlog.enums;

/**
 * All auditable business events in the system.
 *
 * <p>Naming convention: {@code <ENTITY>_<VERB>} — keeps event names self-describing
 * without needing to join on the {@code entity_type} column.
 */
public enum AuditAction {

    // ─── Auth ────────────────────────────────────────────────────────────────
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    TOKEN_REFRESH,

    // ─── User ────────────────────────────────────────────────────────────────
    USER_REGISTERED,
    USER_UPDATED,
    USER_DISABLED,
    USER_ENABLED,

    // ─── Product ─────────────────────────────────────────────────────────────
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_PUBLISHED,
    PRODUCT_DELETED,

    // ─── Category ────────────────────────────────────────────────────────────
    CATEGORY_CREATED,
    CATEGORY_UPDATED,
    CATEGORY_DELETED,

    // ─── Brand ───────────────────────────────────────────────────────────────
    BRAND_CREATED,
    BRAND_UPDATED,
    BRAND_DELETED,

    // ─── Order ───────────────────────────────────────────────────────────────
    ORDER_CREATED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ORDER_COMPLETED,

    // ─── Payment ─────────────────────────────────────────────────────────────
    PAYMENT_COD_COMPLETED,
    PAYMENT_CALLBACK_SUCCESS,
    PAYMENT_CALLBACK_FAILED,

    // ─── Inventory ───────────────────────────────────────────────────────────
    INVENTORY_ADJUSTED,

    // ─── Voucher / Promotion ─────────────────────────────────────────────────
    PROMOTION_CREATED,
    PROMOTION_UPDATED,
    PROMOTION_DELETED,
    VOUCHER_CREATED,
    VOUCHER_UPDATED,
    VOUCHER_APPLIED,
    VOUCHER_RELEASED,

    // ─── Shipment / Invoice ──────────────────────────────────────────────────
    SHIPMENT_CREATED,
    SHIPMENT_STATUS_UPDATED,
    INVOICE_GENERATED,
    INVOICE_STATUS_UPDATED,

    // ─── Review ──────────────────────────────────────────────────────────────
    REVIEW_SUBMITTED,
    REVIEW_APPROVED,
    REVIEW_REJECTED
}
