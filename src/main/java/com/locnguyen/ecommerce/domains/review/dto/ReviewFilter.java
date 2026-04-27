package com.locnguyen.ecommerce.domains.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Review filter parameters")
public class ReviewFilter {

    @Schema(description = "Filter by review status (PENDING, APPROVED, REJECTED)")
    private String status;

    @Schema(description = "Filter by product ID")
    private Long productId;

    @Schema(description = "Filter by customer ID")
    private Long customerId;

    @Schema(description = "Minimum rating (1–5)")
    private Integer minRating;

    @Schema(description = "Maximum rating (1–5)")
    private Integer maxRating;
}
