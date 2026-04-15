package com.locnguyen.ecommerce.domains.product.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * A concrete value for a {@link ProductAttribute} — e.g., "M", "Red", "Cotton".
 */
@Entity
@Table(name = "product_attribute_values")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ProductAttributeValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    @ToString.Exclude
    private ProductAttribute attribute;

    @Column(name = "value", length = 100, nullable = false)
    private String value;

    @Column(name = "display_value", length = 100)
    private String displayValue;

    @ManyToMany(mappedBy = "attributeValues")
    @ToString.Exclude
    private Set<com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant> variants = new HashSet<>();
}
