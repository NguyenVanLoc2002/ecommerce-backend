package com.locnguyen.ecommerce.domains.brand.specification;

import com.locnguyen.ecommerce.domains.brand.dto.BrandFilter;
import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class BrandSpecification {

    private BrandSpecification() {}

    public static Specification<Brand> withFilter(BrandFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(cb.like(
                        cb.upper(root.get("name")),
                        "%" + filter.getName().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                try {
                    BrandStatus status = BrandStatus.valueOf(filter.getStatus().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), status));
                } catch (IllegalArgumentException ignored) {
                    // unknown status value — skip predicate
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
