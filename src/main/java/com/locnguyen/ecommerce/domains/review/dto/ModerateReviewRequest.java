package com.locnguyen.ecommerce.domains.review.dto;

import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ModerateReviewRequest {

    /** Must be either {@link ReviewStatus#APPROVED} or {@link ReviewStatus#REJECTED}. */
    @NotNull(message = "Action is required")
    private ReviewStatus action;

    @Size(max = 500, message = "Admin note must not exceed 500 characters")
    private String adminNote;
}
