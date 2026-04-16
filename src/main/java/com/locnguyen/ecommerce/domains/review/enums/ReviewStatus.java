package com.locnguyen.ecommerce.domains.review.enums;

/**
 * Lifecycle status of a customer review.
 *
 * <ul>
 *   <li>{@code PENDING}  — newly submitted, awaiting moderation</li>
 *   <li>{@code APPROVED} — visible on the product page</li>
 *   <li>{@code REJECTED} — hidden; customer is notified with optional admin note</li>
 * </ul>
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
