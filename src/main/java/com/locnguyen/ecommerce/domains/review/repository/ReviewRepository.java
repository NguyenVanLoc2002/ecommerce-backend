package com.locnguyen.ecommerce.domains.review.repository;

import com.locnguyen.ecommerce.domains.review.entity.Review;
import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

    /** Check whether an order item has already been reviewed (prevents duplicates). */
    boolean existsByOrderItemId(UUID orderItemId);

    Optional<Review> findByIdAndDeletedFalse(UUID id);

    /** All reviews by a customer, newest first â€” for "my reviews" feed. */
    Page<Review> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Review> findByCustomerIdAndDeletedFalseOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    /** Approved reviews for a product â€” shown on the public product page. */
    Page<Review> findByProductIdAndStatusOrderByCreatedAtDesc(
            UUID productId, ReviewStatus status, Pageable pageable);

    /** Approved reviews for a specific variant. */
    Page<Review> findByVariantIdAndStatusOrderByCreatedAtDesc(
            UUID variantId, ReviewStatus status, Pageable pageable);

    /** All reviews pending moderation â€” for the admin moderation queue. */
    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);
}
