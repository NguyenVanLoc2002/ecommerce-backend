package com.locnguyen.ecommerce.domains.review.dto;

import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;
@Data
@Schema(description = "Review filter parameters")
public class ReviewFilter {

    @Schema(description = "Filter by review status (PENDING, APPROVED, REJECTED)")
    private ReviewStatus status;

    @Schema(description = "Filter by product ID")
    private UUID productId;

    @Schema(description = "Filter by customer ID")
    private UUID customerId;

    @Schema(description = "Minimum rating (1–5)")
    private Integer minRating;

    @Schema(description = "Maximum rating (1–5)")
    private Integer maxRating;

    @Schema(description = "Soft delete filter: false=active only, true=deleted only")
    private Boolean isDeleted;

    @Schema(description = "Include both active and deleted rows")
    private Boolean includeDeleted;
}
