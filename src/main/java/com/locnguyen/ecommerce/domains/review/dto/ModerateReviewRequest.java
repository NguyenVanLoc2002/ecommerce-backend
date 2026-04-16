package com.locnguyen.ecommerce.domains.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ModerateReviewRequest {

    /** Must be either {@code APPROVED} or {@code REJECTED}. */
    @NotBlank(message = "Action is required")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Action must be APPROVED or REJECTED")
    private String action;

    @Size(max = 500, message = "Admin note must not exceed 500 characters")
    private String adminNote;
}
