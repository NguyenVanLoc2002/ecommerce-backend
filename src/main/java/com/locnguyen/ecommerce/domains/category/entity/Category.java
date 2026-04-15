package com.locnguyen.ecommerce.domains.category.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * Hierarchical category — supports parent-child nesting (e.g., Nam → Áo nam).
 * Self-referencing via {@code parent_id}.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Category extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @ToString.Exclude
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @ToString.Exclude
    private Set<Category> children = new HashSet<>();

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "slug", length = 255, nullable = false, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private CategoryStatus status = CategoryStatus.ACTIVE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @ManyToMany(mappedBy = "categories")
    @ToString.Exclude
    private Set<com.locnguyen.ecommerce.domains.product.entity.Product> products = new HashSet<>();
}
