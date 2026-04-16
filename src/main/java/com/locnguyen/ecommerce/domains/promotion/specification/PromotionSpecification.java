package com.locnguyen.ecommerce.domains.promotion.specification;

import com.locnguyen.ecommerce.domains.promotion.dto.PromotionFilter;
import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import com.locnguyen.ecommerce.domains.promotion.enums.PromotionScope;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PromotionSpecification {

    private PromotionSpecification() {}

    public static Specification<Promotion> withFilter(PromotionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(cb.like(
                        cb.upper(root.get("name")),
                        "%" + filter.getName().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getScope() != null && !filter.getScope().isBlank()) {
                try {
                    PromotionScope scope = PromotionScope.valueOf(
                            filter.getScope().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("scope"), scope));
                } catch (IllegalArgumentException ignored) {
                    // unknown scope value — skip predicate
                }
            }

            if (filter.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), filter.getActive()));
            }

            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("startDate"),
                        filter.getDateFrom().atStartOfDay()
                ));
            }

            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("endDate"),
                        filter.getDateTo().atTime(23, 59, 59)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
