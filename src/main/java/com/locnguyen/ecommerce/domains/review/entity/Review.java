package com.locnguyen.ecommerce.domains.review.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Customer product review submitted after a completed order.
 *
 * <p>Eligibility rule: the {@link OrderItem} referenced by {@code orderItem}
 * must belong to an {@link com.locnguyen.ecommerce.domains.order.entity.Order}
 * whose status is {@link com.locnguyen.ecommerce.domains.order.enums.OrderStatus#COMPLETED}.
 *
 * <p>One review per order item (unique constraint on {@code order_item_id}).
 * A customer who purchased the same product in separate orders may submit
 * one review per purchase.
 *
 * <p>Reviews start as {@link ReviewStatus#PENDING} and become visible only
 * after being {@link ReviewStatus#APPROVED} by staff/admin.
 *
 * <p>Extends {@link SoftDeleteEntity} — reviews can be soft-deleted by admin.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = @UniqueConstraint(name = "uc_review_order_item", columnNames = "order_item_id")
)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Review extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    @ToString.Exclude
    private ProductVariant variant;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    @ToString.Exclude
    private OrderItem orderItem;

    /** Star rating 1–5. */
    @Column(name = "rating", nullable = false)
    private Integer rating;

    /** Optional free-text review body. */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    /** Admin note shown to the customer when the review is rejected. */
    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    @Column(name = "moderated_by", length = 100)
    private String moderatedBy;
}
