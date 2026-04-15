package com.locnguyen.ecommerce.domains.product.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.category.entity.Category;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Product entity — the parent/catalog-level item.
 * Individual sellable units are {@link com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant}.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Product extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    @ToString.Exclude
    private Brand brand;

    @ManyToMany
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @ToString.Exclude
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant> variants = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<ProductMedia> media = new HashSet<>();

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "slug", length = 255, nullable = false, unique = true)
    private String slug;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;
}
