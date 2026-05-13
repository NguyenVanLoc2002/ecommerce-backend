package com.locnguyen.ecommerce.domains.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 500) String reason,
        @Size(max = 500) String note
) {}
