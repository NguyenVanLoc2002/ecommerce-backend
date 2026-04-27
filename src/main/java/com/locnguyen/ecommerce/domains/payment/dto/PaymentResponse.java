package com.locnguyen.ecommerce.domains.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment response")
public class PaymentResponse {

    private final Long id;
    private final Long orderId;
    private final String orderCode;
    private final String paymentCode;
    private final PaymentMethod method;
    private final PaymentRecordStatus status;
    private final BigDecimal amount;
    private final LocalDateTime paidAt;
    private final List<TransactionResponse> transactions;
    private final LocalDateTime createdAt;
}
