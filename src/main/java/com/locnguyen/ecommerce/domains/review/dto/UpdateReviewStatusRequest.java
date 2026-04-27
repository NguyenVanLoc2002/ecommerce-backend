package com.locnguyen.ecommerce.domains.review.dto;

import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Admin request to approve or reject a review")
public class UpdateReviewStatusRequest {

    @NotNull
    @Schema(description = "Target status: APPROVED or REJECTED")
    private ReviewStatus status;

    @Size(max = 500)
    @Schema(description = "Internal moderation note — not visible to the customer")
    private String adminNote;
}
