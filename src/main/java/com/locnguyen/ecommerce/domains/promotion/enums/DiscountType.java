package com.locnguyen.ecommerce.domains.promotion.enums;

/**
 * How the discount is calculated.
 *
 * <ul>
 *   <li>{@link #PERCENTAGE} — deduct a percentage of the order amount (e.g., 20 → 20 %).
 *       An optional {@code maxDiscountAmount} on the promotion caps the final deduction.</li>
 *   <li>{@link #FIXED_AMOUNT} — deduct a flat monetary amount (e.g., 50 000 VND).</li>
 * </ul>
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
}
