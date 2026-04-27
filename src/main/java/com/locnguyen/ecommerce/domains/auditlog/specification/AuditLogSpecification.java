package com.locnguyen.ecommerce.domains.auditlog.specification;

import com.locnguyen.ecommerce.domains.auditlog.dto.AuditLogFilter;
import com.locnguyen.ecommerce.domains.auditlog.entity.AuditLog;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AuditLogSpecification {

    private AuditLogSpecification() {}

    public static Specification<AuditLog> withFilter(AuditLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filter.getEntityType() != null && !filter.getEntityType().isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(root.get("entityType")),
                        filter.getEntityType().trim().toUpperCase()
                ));
            }

            if (filter.getEntityId() != null && !filter.getEntityId().isBlank()) {
                predicates.add(cb.equal(
                        root.get("entityId"),
                        filter.getEntityId().trim()
                ));
            }

            if (filter.getAction() != null && !filter.getAction().isBlank()) {
                try {
                    AuditAction action = AuditAction.valueOf(filter.getAction().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("action"), action));
                } catch (IllegalArgumentException ignored) {
                    // unknown action — skip predicate rather than failing
                }
            }

            if (filter.getActor() != null && !filter.getActor().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("actor")),
                        "%" + filter.getActor().trim().toLowerCase() + "%"
                ));
            }

            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getFromDate().atStartOfDay()
                ));
            }

            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getToDate().atTime(23, 59, 59)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
