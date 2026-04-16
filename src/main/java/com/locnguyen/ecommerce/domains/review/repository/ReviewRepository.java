package com.locnguyen.ecommerce.domains.review.repository;

import com.locnguyen.ecommerce.domains.review.entity.Review;
import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Check whether an order item has already been reviewed (prevents duplicates). */
    boolean existsByOrderItemId(Long orderItemId);

    /** All reviews by a customer, newest first — for "my reviews" feed. */
    Page<Review> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /** Approved reviews for a product — shown on the public product page. */
    Page<Review> findByProductIdAndStatusOrderByCreatedAtDesc(
            Long productId, ReviewStatus status, Pageable pageable);

    /** Approved reviews for a specific variant. */
    Page<Review> findByVariantIdAndStatusOrderByCreatedAtDesc(
            Long variantId, ReviewStatus status, Pageable pageable);

    /** All reviews pending moderation — for the admin moderation queue. */
    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);
}
