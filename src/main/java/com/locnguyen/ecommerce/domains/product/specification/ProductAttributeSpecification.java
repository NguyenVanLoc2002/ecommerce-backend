package com.locnguyen.ecommerce.domains.product.specification;

import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeFilter;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttribute;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductAttributeSpecification {

    private ProductAttributeSpecification() {}

    public static Specification<ProductAttribute> withFilter(ProductAttributeFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.and();
            }

            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("type"), filter.getType()));
            }

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String like = "%" + filter.getKeyword().trim().toUpperCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.upper(root.get("name")), like),
                        cb.like(cb.upper(root.get("code")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
