package com.locnguyen.ecommerce.domains.product.specification;

import com.locnguyen.ecommerce.domains.product.dto.ProductFilter;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic JPA Specification for product list queries.
 * Builds predicates from optional {@link ProductFilter} fields.
 */
public final class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> withFilter(ProductFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Keyword — search product name
            if (StringUtils.hasText(filter.getKeyword())) {
                String pattern = "%" + filter.getKeyword().toLowerCase().trim() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            // Category — join product_categories
            if (filter.getCategoryId() != null) {
                Join<Object, Object> categoryJoin = root.join("categories", JoinType.INNER);
                predicates.add(cb.equal(categoryJoin.get("id"), filter.getCategoryId()));
            }

            // Brand
            if (filter.getBrandId() != null) {
                predicates.add(cb.equal(root.get("brand").get("id"), filter.getBrandId()));
            }

            // Status
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // Featured
            if (filter.getFeatured() != null) {
                predicates.add(cb.equal(root.get("featured"), filter.getFeatured()));
            }

            // Price range — EXISTS subquery on variant effective price (coalesce sale/base)
            if (filter.getMinPrice() != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<ProductVariant> pv = sq.from(ProductVariant.class);
                Expression<BigDecimal> price = cb.coalesce(pv.get("salePrice"), pv.get("basePrice"));
                sq.select(cb.literal(1L))
                  .where(cb.equal(pv.get("product"), root),
                         cb.ge(price, filter.getMinPrice()));
                predicates.add(cb.exists(sq));
            }

            if (filter.getMaxPrice() != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<ProductVariant> pv = sq.from(ProductVariant.class);
                Expression<BigDecimal> price = cb.coalesce(pv.get("salePrice"), pv.get("basePrice"));
                sq.select(cb.literal(1L))
                  .where(cb.equal(pv.get("product"), root),
                         cb.le(price, filter.getMaxPrice()));
                predicates.add(cb.exists(sq));
            }

            return predicates.isEmpty()
                    ? null
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
