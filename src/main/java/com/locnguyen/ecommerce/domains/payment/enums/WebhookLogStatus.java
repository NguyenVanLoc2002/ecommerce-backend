package com.locnguyen.ecommerce.domains.payment.enums;

/**
 * Lifecycle status for a {@link com.locnguyen.ecommerce.domains.payment.entity.PaymentWebhookLog}.
 *
 * <p>RECEIVED  — log created; processing not yet attempted.
 * <p>PROCESSED — callback was fully handled and payment state updated.
 * <p>IGNORED   — callback was valid but no action was needed (duplicate or irrelevant).
 * <p>FAILED    — processing raised an error; see error_message for details.
 */
public enum WebhookLogStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED
}
