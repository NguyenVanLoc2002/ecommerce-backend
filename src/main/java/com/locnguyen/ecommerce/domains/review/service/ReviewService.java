package com.locnguyen.ecommerce.domains.review.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.notification.enums.NotificationType;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderItemRepository;
import com.locnguyen.ecommerce.domains.review.dto.CreateReviewRequest;
import com.locnguyen.ecommerce.domains.review.dto.ModerateReviewRequest;
import com.locnguyen.ecommerce.domains.review.dto.ReviewFilter;
import com.locnguyen.ecommerce.domains.review.dto.ReviewResponse;
import com.locnguyen.ecommerce.domains.review.dto.UpdateReviewStatusRequest;
import com.locnguyen.ecommerce.domains.review.entity.Review;
import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import com.locnguyen.ecommerce.domains.review.mapper.ReviewMapper;
import com.locnguyen.ecommerce.domains.review.repository.ReviewRepository;
import com.locnguyen.ecommerce.domains.review.specification.ReviewSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewMapper reviewMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    // ─── Customer operations ─────────────────────────────────────────────────

    /**
     * Submit a review for an order item.
     *
     * <p>Eligibility rules enforced here:
     * <ol>
     *   <li>The order item must exist and belong to {@code customer}.</li>
     *   <li>The parent order must be in {@link OrderStatus#COMPLETED} status.</li>
     *   <li>No review must already exist for this order item.</li>
     * </ol>
     */
    @Transactional
    public ReviewResponse createReview(Customer customer, CreateReviewRequest request) {
        // 1. Load order item
        OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_ELIGIBLE));

        // 2. Ownership check — use ORDER_NOT_FOUND to avoid leaking existence
        Order order = orderItem.getOrder();
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 3. Order must be COMPLETED
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.REVIEW_NOT_ELIGIBLE);
        }

        // 4. Duplicate check
        if (reviewRepository.existsByOrderItemId(orderItem.getId())) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 5. Persist review
        Review review = new Review();
        review.setCustomer(customer);
        review.setProduct(orderItem.getVariant().getProduct());
        review.setVariant(orderItem.getVariant());
        review.setOrderItem(orderItem);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setStatus(ReviewStatus.PENDING);

        review = reviewRepository.save(review);

        // 6. Notify customer that review is under moderation
        notificationService.send(
                customer,
                NotificationType.REVIEW_SUBMITTED,
                "Review submitted",
                "Your review for \"" + orderItem.getProductName() + "\" has been submitted and is under review.",
                review.getId(),
                "REVIEW"
        );

        log.info("Review submitted: id={} customerId={} orderItemId={} rating={}",
                review.getId(), customer.getId(), orderItem.getId(), request.getRating());
        auditLogService.log(AuditAction.REVIEW_SUBMITTED, "REVIEW", String.valueOf(review.getId()),
                "orderItemId=" + orderItem.getId() + " rating=" + request.getRating());

        return reviewMapper.toResponse(review);
    }

    // ─── Read operations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(UUID id) {
        return reviewMapper.toResponse(findOrThrow(id));
    }

    /** Returns APPROVED reviews for a product — public endpoint. Supports rating filter. */
    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getProductReviews(UUID productId, ReviewFilter filter, Pageable pageable) {
        filter.setProductId(productId);
        filter.setStatus(ReviewStatus.APPROVED);
        Page<Review> page = reviewRepository.findAll(ReviewSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(reviewMapper::toResponse));
    }

    /** Returns all reviews submitted by the authenticated customer. */
    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getMyReviews(Customer customer, Pageable pageable) {
        Page<Review> page = reviewRepository
                .findByCustomerIdAndDeletedFalseOrderByCreatedAtDesc(customer.getId(), pageable);
        return PagedResponse.of(page.map(reviewMapper::toResponse));
    }

    /** Returns reviews in the PENDING moderation queue — admin/staff only. Supports additional filtering. */
    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getPendingReviews(ReviewFilter filter, Pageable pageable) {
        if (filter.getStatus() == null) {
            filter.setStatus(ReviewStatus.PENDING);
        }
        Page<Review> page = reviewRepository.findAll(ReviewSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(reviewMapper::toResponse));
    }

    /** Admin list: any status, fully filterable. */
    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> listReviews(ReviewFilter filter, Pageable pageable) {
        Page<Review> page = reviewRepository.findAll(ReviewSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(reviewMapper::toResponse));
    }

    /** Admin lookup by ID — alias for {@link #getReviewById(Long)}. */
    @Transactional(readOnly = true)
    public ReviewResponse adminGetById(UUID id) {
        return getReviewById(id);
    }

    /**
     * Status-update overload accepting {@link UpdateReviewStatusRequest}.
     * Delegates to the canonical moderation flow.
     */
    @Transactional
    public ReviewResponse moderateReview(UUID reviewId, UpdateReviewStatusRequest request) {
        ModerateReviewRequest delegate = new ModerateReviewRequest();
        delegate.setAction(request.getStatus());
        delegate.setAdminNote(request.getAdminNote());
        return moderateReview(reviewId, delegate);
    }

    // ─── Admin / Staff operations ────────────────────────────────────────────

    /**
     * Approve or reject a pending review.
     *
     * <p>Throws {@link ErrorCode#REVIEW_ALREADY_MODERATED} if the review is
     * not in {@link ReviewStatus#PENDING} state.
     */
    @Transactional
    public ReviewResponse moderateReview(UUID reviewId, ModerateReviewRequest request) {
        Review review = findOrThrow(reviewId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        if (review.getStatus() != ReviewStatus.PENDING) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_MODERATED);
        }

        ReviewStatus newStatus = request.getAction();
        if (newStatus != ReviewStatus.APPROVED && newStatus != ReviewStatus.REJECTED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Action must be APPROVED or REJECTED");
        }
        review.setStatus(newStatus);
        review.setAdminNote(request.getAdminNote());
        review.setModeratedAt(LocalDateTime.now());
        review.setModeratedBy(actor);

        review = reviewRepository.save(review);

        // Notify customer of the moderation outcome
        Customer customer = review.getCustomer();
        String productName = review.getVariant().getProduct().getName();

        if (newStatus == ReviewStatus.APPROVED) {
            notificationService.send(
                    customer,
                    NotificationType.REVIEW_APPROVED,
                    "Review approved",
                    "Your review for \"" + productName + "\" has been approved and is now visible.",
                    review.getId(),
                    "REVIEW"
            );
            auditLogService.log(AuditAction.REVIEW_APPROVED, "REVIEW", String.valueOf(reviewId));
        } else {
            String noteDetail = request.getAdminNote() != null ? " Reason: " + request.getAdminNote() : "";
            notificationService.send(
                    customer,
                    NotificationType.REVIEW_REJECTED,
                    "Review not published",
                    "Your review for \"" + productName + "\" could not be published." + noteDetail,
                    review.getId(),
                    "REVIEW"
            );
            auditLogService.log(AuditAction.REVIEW_REJECTED, "REVIEW", String.valueOf(reviewId),
                    "adminNote=" + request.getAdminNote());
        }

        log.info("Review moderated: id={} status={} by={}", reviewId, newStatus, actor);
        return reviewMapper.toResponse(review);
    }

    /**
     * Soft-delete a review — admin only.
     */
    @Transactional
    public void deleteReview(UUID reviewId) {
        Review review = findOrThrow(reviewId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        review.softDelete(actor);
        reviewRepository.save(review);
        log.info("Review soft-deleted: id={} by={}", reviewId, actor);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Review findOrThrow(UUID id) {
        return reviewRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));
    }
}
