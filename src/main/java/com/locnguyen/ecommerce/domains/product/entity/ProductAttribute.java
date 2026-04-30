package com.locnguyen.ecommerce.domains.product.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Defines a product attribute type — e.g., "Size", "Color", "Material".
 *
 * <p>{@link AttributeType#VARIANT} attributes generate product variants.
 * {@link AttributeType#DESCRIPTIVE} attributes are informational only.
 */
@Entity
@Table(name = "product_attributes")
@Getter
@Setter
@NoArgsConstructor
public class ProductAttribute extends SoftDeleteEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private AttributeType type = AttributeType.VARIANT;

    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductAttributeValue> values = new HashSet<>();
}
