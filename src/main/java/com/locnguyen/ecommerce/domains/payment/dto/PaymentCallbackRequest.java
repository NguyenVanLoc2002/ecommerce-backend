package com.locnguyen.ecommerce.domains.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Payment callback request — from payment gateway")
public class PaymentCallbackRequest {

    @NotBlank(message = "Order code is required")
    @Schema(example = "ORD20260408123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderCode;

    @NotBlank(message = "Transaction status is required")
    @Schema(example = "SUCCESS", description = "SUCCESS or FAILED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "TXN_GW_12345", description = "Provider's transaction ID")
    private String providerTxnId;

    @Schema(example = "VNPAY", description = "Payment provider identifier")
    private String provider;

    @Schema(description = "Raw callback payload from gateway (JSON)")
    private String payload;
}
