package com.locnguyen.ecommerce.domains.inventory.specification;

import com.locnguyen.ecommerce.domains.inventory.dto.InventoryFilter;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InventorySpecification {

    private InventorySpecification() {}

    public static Specification<Inventory> withFilter(InventoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            var variantJoin = root.join("variant", JoinType.INNER);
            var warehouseJoin = root.join("warehouse", JoinType.INNER);
            var productJoin = variantJoin.join("product", JoinType.INNER);

            if (filter.getVariantId() != null) {
                predicates.add(cb.equal(variantJoin.get("id"), filter.getVariantId()));
            }

            if (filter.getWarehouseId() != null) {
                predicates.add(cb.equal(warehouseJoin.get("id"), filter.getWarehouseId()));
            }

            if (filter.getProductId() != null) {
                predicates.add(cb.equal(productJoin.get("id"), filter.getProductId()));
            }

            if (filter.getSku() != null && !filter.getSku().isBlank()) {
                predicates.add(cb.like(
                        cb.upper(variantJoin.get("sku")),
                        "%" + filter.getSku().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String keyword = "%" + filter.getKeyword().trim().toUpperCase() + "%";

                predicates.add(cb.or(
                        cb.like(cb.upper(variantJoin.get("sku")), keyword),
                        cb.like(cb.upper(variantJoin.get("variantName")), keyword),
                        cb.like(cb.upper(warehouseJoin.get("name")), keyword)
                ));
            }

            if (filter.getVariantStatus() != null && !filter.getVariantStatus().isBlank()) {
                try {
                    ProductVariantStatus status = ProductVariantStatus.valueOf(
                            filter.getVariantStatus().trim().toUpperCase()
                    );
                    predicates.add(cb.equal(variantJoin.get("status"), status));
                } catch (IllegalArgumentException ignored) {
                    // invalid status -> skip predicate
                }
            }

            Expression<Integer> availableExpr = cb.diff(root.get("onHand"), root.get("reserved"));

            if (Boolean.TRUE.equals(filter.getOutOfStock())) {
                predicates.add(cb.lessThanOrEqualTo(availableExpr, 0));
            }

            if (Boolean.TRUE.equals(filter.getLowStock())) {
                int threshold = filter.getLowStockThreshold() != null
                        ? filter.getLowStockThreshold()
                        : 5;
                predicates.add(cb.lessThanOrEqualTo(availableExpr, threshold));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}