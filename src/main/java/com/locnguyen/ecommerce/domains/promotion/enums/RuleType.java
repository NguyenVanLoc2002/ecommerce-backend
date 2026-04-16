package com.locnguyen.ecommerce.domains.promotion.enums;

/**
 * Types of conditions a promotion rule can enforce.
 *
 * <p>Rule value format per type:
 * <ul>
 *   <li>{@link #MIN_ORDER_AMOUNT}      — decimal string, e.g. {@code "200000.00"}</li>
 *   <li>{@link #SPECIFIC_PRODUCTS}     — comma-separated product IDs, e.g. {@code "1,2,3"}</li>
 *   <li>{@link #SPECIFIC_CATEGORIES}   — comma-separated category IDs, e.g. {@code "5,6"}</li>
 *   <li>{@link #SPECIFIC_BRANDS}       — comma-separated brand IDs, e.g. {@code "2"}</li>
 *   <li>{@link #FIRST_ORDER}           — {@code "true"} (customer must have no prior orders)</li>
 * </ul>
 */
public enum RuleType {
    MIN_ORDER_AMOUNT,
    SPECIFIC_PRODUCTS,
    SPECIFIC_CATEGORIES,
    SPECIFIC_BRANDS,
    FIRST_ORDER
}
