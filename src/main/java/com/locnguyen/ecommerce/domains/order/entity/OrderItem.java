package com.locnguyen.ecommerce.domains.order.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Order line item — immutable snapshot of product data at checkout time.
 *
 * <p>Never depends on live product/variant data. All display and pricing fields
 * are captured at order creation time so the order remains accurate even if
 * products are updated, deactivated, or deleted afterwards.
 *
 * <p>Extends {@link BaseEntity} (no soft delete) — order items are permanent records.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"order", "variant"})
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    // ─── Product snapshot ───────────────────────────────────────────────────

    @Column(name = "product_name", length = 255, nullable = false)
    private String productName;

    @Column(name = "variant_name", length = 255, nullable = false)
    private String variantName;

    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    // ─── Pricing snapshot ───────────────────────────────────────────────────

    @Column(name = "unit_price", precision = 18, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "sale_price", precision = 18, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "line_total", precision = 18, scale = 2, nullable = false)
    private BigDecimal lineTotal;
}
