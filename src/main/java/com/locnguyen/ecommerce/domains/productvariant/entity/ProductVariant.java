package com.locnguyen.ecommerce.domains.productvariant.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * The sellable unit. A product "Áo thun nam" has variants like "Trắng / M", "Đen / L".
 *
 * <p>Contains pricing (base/sale/compare-at) and SKU.
 * Links to attribute values (size, color) via many-to-many.
 */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ProductVariant extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    @ManyToMany
    @JoinTable(
            name = "variant_attribute_values",
            joinColumns = @JoinColumn(name = "variant_id"),
            inverseJoinColumns = @JoinColumn(name = "attribute_value_id")
    )
    @ToString.Exclude
    private Set<ProductAttributeValue> attributeValues = new HashSet<>();

    @Column(name = "sku", length = 100, nullable = false, unique = true)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "variant_name", length = 255, nullable = false)
    private String variantName;

    @Column(name = "base_price", precision = 18, scale = 2, nullable = false)
    private BigDecimal basePrice;

    @Column(name = "sale_price", precision = 18, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "compare_at_price", precision = 18, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "weight_gram")
    private Integer weightGram;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private ProductVariantStatus status = ProductVariantStatus.ACTIVE;
}
