package com.locnguyen.ecommerce.domains.invoice.specification;

import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceFilter;
import com.locnguyen.ecommerce.domains.invoice.entity.Invoice;
import com.locnguyen.ecommerce.domains.invoice.enums.InvoiceStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InvoiceSpecification {

    private InvoiceSpecification() {}

    public static Specification<Invoice> withFilter(InvoiceFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getInvoiceCode() != null && !filter.getInvoiceCode().isBlank()) {
                predicates.add(cb.like(
                        cb.upper(root.get("invoiceCode")),
                        "%" + filter.getInvoiceCode().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getOrderCode() != null && !filter.getOrderCode().isBlank()) {
                var orderJoin = root.join("order", JoinType.INNER);
                predicates.add(cb.like(
                        cb.upper(orderJoin.get("orderCode")),
                        "%" + filter.getOrderCode().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                try {
                    InvoiceStatus status = InvoiceStatus.valueOf(
                            filter.getStatus().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), status));
                } catch (IllegalArgumentException ignored) {
                    // unknown status — skip predicate
                }
            }

            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("issuedAt"),
                        filter.getDateFrom().atStartOfDay()
                ));
            }

            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("issuedAt"),
                        filter.getDateTo().atTime(23, 59, 59)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
