package com.locnguyen.ecommerce.domains.notification.enums;

/**
 * Categories of in-app notifications sent to customers.
 */
public enum NotificationType {

    // ─── Order lifecycle ────────────────────────────────────────────────────
    ORDER_PLACED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_COMPLETED,

    // ─── Review ─────────────────────────────────────────────────────────────
    REVIEW_SUBMITTED,
    REVIEW_APPROVED,
    REVIEW_REJECTED,

    // ─── Payment ────────────────────────────────────────────────────────────
    PAYMENT_RECEIVED,
    PAYMENT_FAILED,

    // ─── Promotion ──────────────────────────────────────────────────────────
    VOUCHER_RECEIVED,

    // ─── System ─────────────────────────────────────────────────────────────
    SYSTEM
}
