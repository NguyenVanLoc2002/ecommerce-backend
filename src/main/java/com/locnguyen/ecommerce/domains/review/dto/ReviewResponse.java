package com.locnguyen.ecommerce.domains.review.dto;

import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private Long id;

    private Long customerId;
    private String customerName;

    private Long productId;
    private String productName;
    private Long variantId;
    private String variantName;
    private String sku;

    private Long orderItemId;

    private Integer rating;
    private String comment;

    private ReviewStatus status;
    private String adminNote;
    private LocalDateTime moderatedAt;
    private String moderatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
