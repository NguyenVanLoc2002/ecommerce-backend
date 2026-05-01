package com.locnguyen.ecommerce.common.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.List;

public final class SoftDeleteSpecificationHelper {

    private SoftDeleteSpecificationHelper() {}

    public static void addDeletedFilter(List<Predicate> predicates,
                                        Path<Boolean> deletedPath,
                                        CriteriaBuilder cb,
                                        Boolean isDeleted,
                                        Boolean includeDeleted) {
        if (Boolean.TRUE.equals(includeDeleted)) {
            return;
        }

        if (isDeleted != null) {
            predicates.add(isDeleted ? cb.isTrue(deletedPath) : cb.isFalse(deletedPath));
            return;
        }

        predicates.add(cb.isFalse(deletedPath));
    }
}
