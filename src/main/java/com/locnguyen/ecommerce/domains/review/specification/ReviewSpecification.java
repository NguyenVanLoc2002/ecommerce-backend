package com.locnguyen.ecommerce.domains.review.specification;

import com.locnguyen.ecommerce.common.specification.SoftDeleteSpecificationHelper;
import com.locnguyen.ecommerce.domains.review.dto.ReviewFilter;
import com.locnguyen.ecommerce.domains.review.entity.Review;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ReviewSpecification {

    private ReviewSpecification() {}

    public static Specification<Review> withFilter(ReviewFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            SoftDeleteSpecificationHelper.addDeletedFilter(
                    predicates,
                    root.get("deleted"),
                    cb,
                    filter != null ? filter.getIsDeleted() : null,
                    filter != null ? filter.getIncludeDeleted() : null
            );

            if (filter != null && filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter != null && filter.getProductId() != null) {
                predicates.add(cb.equal(root.get("product").get("id"), filter.getProductId()));
            }

            if (filter != null && filter.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), filter.getCustomerId()));
            }

            if (filter != null && filter.getMinRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), filter.getMinRating()));
            }

            if (filter != null && filter.getMaxRating() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("rating"), filter.getMaxRating()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
