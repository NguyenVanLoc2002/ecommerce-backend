package com.locnguyen.ecommerce.domains.review.dto;

import com.locnguyen.ecommerce.domains.review.enums.ReviewStatus;
import lombok.*;

import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private UUID id;

    private UUID customerId;
    private String customerName;

    private UUID productId;
    private String productSlug;
    private String productName;
    private String brandName;
    private String productImageUrl;
    private UUID variantId;
    private String variantName;
    private String sku;

    private UUID orderItemId;
    private UUID orderId;

    private Integer rating;
    private String comment;

    private ReviewStatus status;
    private String adminNote;
    private LocalDateTime moderatedAt;
    private String moderatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
