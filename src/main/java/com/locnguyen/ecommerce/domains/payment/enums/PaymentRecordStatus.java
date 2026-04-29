package com.locnguyen.ecommerce.domains.payment.enums;

/**
 * Payment record status — lifecycle of a Payment entity.
 *
 * <p>Separate from {@code order.enums.PaymentStatus} which is the denormalized
 * summary on the Order entity. This enum has richer states.
 *
 * <p>COD: PENDING → PAID (on delivery)
 * <p>Online: PENDING → INITIATED → PAID / FAILED
 */
public enum PaymentRecordStatus {
    PENDING,
    INITIATED,
    PAID,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CANCELLED
}
