package com.locnguyen.ecommerce.domains.payment.enums;

/**
 * Lifecycle status for a {@link com.locnguyen.ecommerce.domains.payment.entity.PaymentRefund}.
 *
 * <p>PENDING    — refund requested but not yet submitted to gateway.
 * <p>PROCESSING — refund submitted to gateway; awaiting confirmation.
 * <p>COMPLETED  — gateway confirmed the refund was settled.
 * <p>FAILED     — gateway rejected or failed the refund request.
 * <p>CANCELLED  — refund request was cancelled before submission.
 */
public enum RefundStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
