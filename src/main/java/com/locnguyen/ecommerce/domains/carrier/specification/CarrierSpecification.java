package com.locnguyen.ecommerce.domains.carrier.specification;

import com.locnguyen.ecommerce.domains.carrier.dto.CarrierFilter;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CarrierSpecification {

    private CarrierSpecification() {}

    public static Specification<Carrier> withFilter(CarrierFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String keyword = "%" + filter.getKeyword().trim().toUpperCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.upper(root.get("code")), keyword),
                        cb.like(cb.upper(root.get("name")), keyword)
                ));
            }

            if (filter.getProviderType() != null) {
                predicates.add(cb.equal(root.get("providerType"), filter.getProviderType()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getEnabled() != null) {
                var configJoin = root.join("config", JoinType.LEFT);
                predicates.add(cb.equal(configJoin.get("enabled"), filter.getEnabled()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
