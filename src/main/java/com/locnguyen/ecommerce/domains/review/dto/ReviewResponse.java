package com.locnguyen.ecommerce.domains.review.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private Long id;

    // ─── Customer ───────────────────────────────────────────────────────────
    private Long customerId;
    private String customerName;

    // ─── Product / Variant ──────────────────────────────────────────────────
    private Long productId;
    private String productName;
    private Long variantId;
    private String variantName;
    private String sku;

    // ─── Source order item ──────────────────────────────────────────────────
    private Long orderItemId;

    // ─── Review content ─────────────────────────────────────────────────────
    private Integer rating;
    private String comment;

    // ─── Moderation ─────────────────────────────────────────────────────────
    private String status;
    private String adminNote;
    private LocalDateTime moderatedAt;
    private String moderatedBy;

    // ─── Audit ──────────────────────────────────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
