package com.locnguyen.ecommerce.domains.review.specification;

import com.locnguyen.ecommerce.domains.review.dto.ReviewFilter;
import com.locnguyen.ecommerce.domains.review.entity.Review;
import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ReviewSpecification {

    private ReviewSpecification() {}

    public static Specification<Review> withFilter(ReviewFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                try {
                    ReviewStatus status = ReviewStatus.valueOf(filter.getStatus().trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), status));
                } catch (IllegalArgumentException ignored) {}
            }

            if (filter.getProductId() != null) {
                predicates.add(cb.equal(root.get("product").get("id"), filter.getProductId()));
            }

            if (filter.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), filter.getCustomerId()));
            }

            if (filter.getMinRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), filter.getMinRating()));
            }

            if (filter.getMaxRating() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("rating"), filter.getMaxRating()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
