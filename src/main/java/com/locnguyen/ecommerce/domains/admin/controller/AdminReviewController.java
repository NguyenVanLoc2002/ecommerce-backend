package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.review.dto.ModerateReviewRequest;
import com.locnguyen.ecommerce.domains.review.dto.ReviewFilter;
import com.locnguyen.ecommerce.domains.review.dto.ReviewResponse;
import com.locnguyen.ecommerce.domains.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin review moderation API.
 *
 * <p>Paths are intentionally kept under {@code /api/v1/reviews/...} for backward
 * compatibility with the existing frontend contract — moderation endpoints have
 * always lived alongside customer review endpoints. Access is restricted via
 * {@link PreAuthorize} role checks.
 */
@Tag(name = "Admin — Review (legacy paths)", description = "Review moderation for admins and staff (kept under /reviews for backwards compatibility)")
@RestController("adminReviewLegacyController")
@RequestMapping(AppConstants.API_V1 + "/reviews")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Get reviews pending moderation (filterable by product, customer, rating, status)")
    @GetMapping("/pending")
    public ApiResponse<PagedResponse<ReviewResponse>> getPendingReviews(
            ReviewFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(reviewService.getPendingReviews(filter, pageable));
    }

    @Operation(summary = "Get a review by ID")
    @GetMapping("/{id}")
    public ApiResponse<ReviewResponse> getReviewById(@PathVariable Long id) {
        return ApiResponse.success(reviewService.getReviewById(id));
    }

    @Operation(summary = "Approve or reject a pending review")
    @PatchMapping("/{id}/moderate")
    public ApiResponse<ReviewResponse> moderateReview(
            @PathVariable Long id,
            @Valid @RequestBody ModerateReviewRequest request) {
        return ApiResponse.success(reviewService.moderateReview(id, request));
    }

    @Operation(summary = "Soft-delete a review")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<Void> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ApiResponse.noContent();
    }
}
