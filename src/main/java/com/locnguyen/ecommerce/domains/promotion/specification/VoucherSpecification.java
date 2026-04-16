package com.locnguyen.ecommerce.domains.promotion.specification;

import com.locnguyen.ecommerce.domains.promotion.dto.VoucherFilter;
import com.locnguyen.ecommerce.domains.promotion.entity.Voucher;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class VoucherSpecification {

    private VoucherSpecification() {}

    public static Specification<Voucher> withFilter(VoucherFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(cb.like(
                        cb.upper(root.get("code")),
                        "%" + filter.getCode().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getPromotionId() != null) {
                predicates.add(cb.equal(root.get("promotion").get("id"), filter.getPromotionId()));
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
