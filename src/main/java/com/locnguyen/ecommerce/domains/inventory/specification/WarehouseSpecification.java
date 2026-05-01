package com.locnguyen.ecommerce.domains.inventory.specification;

import com.locnguyen.ecommerce.common.specification.SoftDeleteSpecificationHelper;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehouseFilter;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class WarehouseSpecification {

    private WarehouseSpecification() {}

    public static Specification<Warehouse> withFilter(WarehouseFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            SoftDeleteSpecificationHelper.addDeletedFilter(
                    predicates,
                    root.get("deleted"),
                    cb,
                    filter != null ? filter.getIsDeleted() : null,
                    filter != null ? filter.getIncludeDeleted() : null
            );

            if (filter != null && filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
