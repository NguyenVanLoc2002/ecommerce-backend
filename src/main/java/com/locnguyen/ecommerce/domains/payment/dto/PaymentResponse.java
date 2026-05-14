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

import java.util.UUID;
@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment response")
public class PaymentResponse {

    private final UUID id;
    private final UUID orderId;
    private final String orderCode;
    private final String paymentCode;
    private final PaymentMethod method;
    private final PaymentRecordStatus status;
    private final BigDecimal amount;
    private final LocalDateTime paidAt;
    /** Web redirect URL for online payment. Null for COD and after payment is settled. */
    private final String paymentUrl;
    /** Mobile app deeplink. Null for COD providers and providers that don't support it. */
    private final String deeplink;
    /**
     * QR code data string — raw data to encode as a QR image, NOT an image URL.
     * Null for providers that don't return QR data.
     */
    private final String qrCodeUrl;
    private final List<TransactionResponse> transactions;
    private final LocalDateTime createdAt;
}
