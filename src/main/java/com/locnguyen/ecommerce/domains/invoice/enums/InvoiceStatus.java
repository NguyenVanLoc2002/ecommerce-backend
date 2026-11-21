package com.locnguyen.ecommerce.domains.invoice.enums;

/**
 * Lifecycle status of an invoice.
 *
 * <pre>
 * ISSUED ──► PAID
 *   │
 *   └──────► VOIDED
 * </pre>
 */
public enum InvoiceStatus {

    /** Invoice has been generated and issued to the customer. */
    ISSUED,

    /** Full payment confirmed. */
    PAID,

    /** A completed refund has reduced the paid amount. */
    PARTIALLY_REFUNDED,

    /** The full payment has been refunded. Terminal. */
    REFUNDED,

    /** Invoice cancelled / voided. Terminal. */
    VOIDED
}
