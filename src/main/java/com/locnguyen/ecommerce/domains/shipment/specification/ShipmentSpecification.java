package com.locnguyen.ecommerce.domains.shipment.specification;

import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentFilter;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ShipmentSpecification {

    private ShipmentSpecification() {}

    public static Specification<Shipment> withFilter(ShipmentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getOrderId() != null) {
                predicates.add(cb.equal(root.get("order").get("id"), filter.getOrderId()));
            }

            if (filter.getOrderCode() != null && !filter.getOrderCode().isBlank()) {
                var orderJoin = root.join("order", JoinType.INNER);
                predicates.add(cb.like(
                        cb.upper(orderJoin.get("orderCode")),
                        "%" + filter.getOrderCode().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getCarrier() != null && !filter.getCarrier().isBlank()) {
                predicates.add(cb.like(
                        cb.upper(root.get("carrier")),
                        "%" + filter.getCarrier().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
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
