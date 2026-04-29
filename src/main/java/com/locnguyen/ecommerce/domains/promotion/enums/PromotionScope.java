package com.locnguyen.ecommerce.domains.promotion.enums;

/**
 * The target scope of a promotion discount.
 *
 * <ul>
 *   <li>{@link #ORDER} — discount applies to the entire order subtotal.</li>
 *   <li>{@link #PRODUCT} — discount applies only to specific products
 *       (defined via a {@code SPECIFIC_PRODUCTS} rule).</li>
 *   <li>{@link #CATEGORY} — discount applies to items in specific categories.</li>
 *   <li>{@link #BRAND} — discount applies to items from specific brands.</li>
 * </ul>
 *
 * <p>For MVP, {@link #ORDER} scope is the primary focus. Scoped discounts
 * ({@link #PRODUCT}, {@link #CATEGORY}, {@link #BRAND}) require order-item
 * data during validation and are fully supported via promotion rules.
 */
public enum PromotionScope {
    ORDER,
    PRODUCT,
    CATEGORY,
    BRAND,
    SHIPPING
}
