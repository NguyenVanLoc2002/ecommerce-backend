package com.locnguyen.ecommerce.domains.product.specification;

import com.locnguyen.ecommerce.common.specification.SoftDeleteSpecificationHelper;
import com.locnguyen.ecommerce.domains.product.dto.ProductFilter;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

            SoftDeleteSpecificationHelper.addDeletedFilter(
                    predicates,
                    root.get("deleted"),
                    cb,
                    filter != null ? filter.getIsDeleted() : null,
                    filter != null ? filter.getIncludeDeleted() : null
            );

            if (filter != null && StringUtils.hasText(filter.getKeyword())) {
                String pattern = "%" + filter.getKeyword().toLowerCase().trim() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            if (filter != null && filter.getCategoryId() != null) {
                Join<Object, Object> categoryJoin = root.join("categories", JoinType.INNER);
                predicates.add(cb.equal(categoryJoin.get("id"), filter.getCategoryId()));
            }

            if (filter != null && filter.getBrandId() != null) {
                predicates.add(cb.equal(root.get("brand").get("id"), filter.getBrandId()));
            }

            if (filter != null && filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter != null && filter.getFeatured() != null) {
                predicates.add(cb.equal(root.get("featured"), filter.getFeatured()));
            }

            if (filter != null && filter.getMinPrice() != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<ProductVariant> pv = sq.from(ProductVariant.class);
                Expression<BigDecimal> price = cb.coalesce(pv.get("salePrice"), pv.get("basePrice"));
                sq.select(cb.literal(1L))
                        .where(cb.equal(pv.get("product"), root),
                                cb.ge(price, filter.getMinPrice()));
                predicates.add(cb.exists(sq));
            }

            if (filter != null && filter.getMaxPrice() != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<ProductVariant> pv = sq.from(ProductVariant.class);
                Expression<BigDecimal> price = cb.coalesce(pv.get("salePrice"), pv.get("basePrice"));
                sq.select(cb.literal(1L))
                        .where(cb.equal(pv.get("product"), root),
                                cb.le(price, filter.getMaxPrice()));
                predicates.add(cb.exists(sq));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
