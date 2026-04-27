package com.locnguyen.ecommerce.domains.review.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.review.dto.CreateReviewRequest;
import com.locnguyen.ecommerce.domains.review.dto.ReviewFilter;
import com.locnguyen.ecommerce.domains.review.dto.ReviewResponse;
import com.locnguyen.ecommerce.domains.review.service.ReviewService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Customer-facing and public review endpoints.
 * Admin moderation lives in
 * {@link com.locnguyen.ecommerce.domains.admin.controller.AdminReviewController}.
 */
@Tag(name = "Review")
@RestController
@RequestMapping(AppConstants.API_V1 + "/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    @Operation(summary = "Submit a review for a completed order item")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.created(reviewService.createReview(userService.getCurrentCustomer(), request));
    }

    @Operation(summary = "Get my submitted reviews")
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PagedResponse<ReviewResponse>> getMyReviews(
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(reviewService.getMyReviews(userService.getCurrentCustomer(), pageable));
    }

    @Operation(summary = "Get approved reviews for a product")
    @GetMapping("/product/{productId}")
    public ApiResponse<PagedResponse<ReviewResponse>> getProductReviews(
            @PathVariable Long productId,
            ReviewFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(reviewService.getProductReviews(productId, filter, pageable));
    }
}
