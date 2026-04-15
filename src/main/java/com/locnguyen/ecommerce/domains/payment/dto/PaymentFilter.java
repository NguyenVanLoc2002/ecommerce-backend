package com.locnguyen.ecommerce.domains.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "Admin payment list filter")
public class PaymentFilter {

    @Schema(example = "COD", description = "Filter by payment method: COD or ONLINE")
    private String method;

    @Schema(example = "PAID", description = "Filter by payment status: PENDING, INITIATED, PAID, FAILED, REFUNDED")
    private String status;

    @Schema(example = "ORD20260408123456", description = "Filter by order code (partial match)")
    private String orderCode;

    @Schema(example = "2026-01-01", description = "Created from date (inclusive), format: yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @Schema(example = "2026-12-31", description = "Created to date (inclusive), format: yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
