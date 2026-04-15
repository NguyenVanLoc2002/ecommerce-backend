package com.locnguyen.ecommerce.domains.brand.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "brands")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Brand extends SoftDeleteEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "slug", length = 255, nullable = false, unique = true)
    private String slug;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private BrandStatus status = BrandStatus.ACTIVE;

    @OneToMany(mappedBy = "brand")
    @ToString.Exclude
    private Set<Product> products = new HashSet<>();
}
