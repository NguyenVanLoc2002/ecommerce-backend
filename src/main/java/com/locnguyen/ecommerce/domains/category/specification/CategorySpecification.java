package com.locnguyen.ecommerce.domains.category.specification;

import com.locnguyen.ecommerce.common.specification.SoftDeleteSpecificationHelper;
import com.locnguyen.ecommerce.domains.category.dto.CategoryFilter;
import com.locnguyen.ecommerce.domains.category.entity.Category;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic JPA Specification for product list queries.
 * Builds predicates from optional {@link CategoryFilter} fields.
 */
public final class CategorySpecification {

    private CategorySpecification() {}

    public static Specification<Category> withFilter(CategoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            SoftDeleteSpecificationHelper.addDeletedFilter(
                    predicates,
                    root.get("deleted"),
                    cb,
                    filter != null ? filter.getIsDeleted() : null,
                    filter != null ? filter.getIncludeDeleted() : null
            );

            if (filter != null && filter.getName() != null && !filter.getName().isBlank()) {
                String pattern = "%" + filter.getName().toLowerCase().trim() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            if (filter != null && filter.getSlug() != null && !filter.getSlug().isBlank()) {
                String pattern = "%" + filter.getSlug().toLowerCase().trim() + "%";
                predicates.add(cb.like(cb.lower(root.get("slug")), pattern));
            }

            if (filter != null && filter.getParentId() != null) {
                predicates.add(cb.equal(root.get("parent").get("id"), filter.getParentId()));
            }

            if (filter != null && filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
