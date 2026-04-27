package com.locnguyen.ecommerce.domains.payment.specification;

import com.locnguyen.ecommerce.domains.payment.dto.PaymentFilter;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PaymentSpecification {

    private PaymentSpecification() {}

    public static Specification<Payment> withFilter(PaymentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getMethod() != null) {
                predicates.add(cb.equal(root.get("method"), filter.getMethod()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getOrderCode() != null && !filter.getOrderCode().isBlank()) {
                var orderJoin = root.join("order", JoinType.INNER);
                predicates.add(cb.like(
                        cb.upper(orderJoin.get("orderCode")),
                        "%" + filter.getOrderCode().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getDateFrom().atStartOfDay()
                ));
            }

            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getDateTo().atTime(23, 59, 59)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
