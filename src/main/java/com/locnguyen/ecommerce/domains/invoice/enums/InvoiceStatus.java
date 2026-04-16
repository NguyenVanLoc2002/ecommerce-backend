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

    /** Full payment confirmed. Terminal. */
    PAID,

    /** Invoice cancelled / voided. Terminal. */
    VOIDED
}
