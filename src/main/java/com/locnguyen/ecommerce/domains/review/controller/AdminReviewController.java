package com.locnguyen.ecommerce.domains.review.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.review.dto.ReviewFilter;
import com.locnguyen.ecommerce.domains.review.dto.ReviewResponse;
import com.locnguyen.ecommerce.domains.review.dto.UpdateReviewStatusRequest;
import com.locnguyen.ecommerce.domains.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Reviews", description = "Review moderation (admin only)")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/reviews")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'STAFF')")
public class AdminReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "List all reviews (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<ReviewResponse>> list(
            ReviewFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));
        return ApiResponse.success(reviewService.listReviews(filter, pageable));
    }

    @Operation(summary = "Get a review by ID")
    @GetMapping("/{id}")
    public ApiResponse<ReviewResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(reviewService.adminGetById(id));
    }

    @Operation(
            summary = "Approve or reject a review",
            description = "Only PENDING reviews can be moderated. " +
                    "The customer is notified automatically on either outcome."
    )
    @PatchMapping("/{id}/status")
    public ApiResponse<ReviewResponse> moderate(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewStatusRequest request) {
        return ApiResponse.success(reviewService.moderateReview(id, request));
    }
}
