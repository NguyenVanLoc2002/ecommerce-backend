package com.locnguyen.ecommerce.domains.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentProviderType;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment transaction response — audit trail entry")
public class TransactionResponse {

    private final UUID id;
    private final String transactionCode;
    private final TransactionStatus status;
    private final BigDecimal amount;
    private final PaymentMethod method;
    private final PaymentProviderType provider;
    private final String providerTxnId;
    private final String referenceType;
    private final String referenceId;
    private final String note;
    private final LocalDateTime createdAt;
}
