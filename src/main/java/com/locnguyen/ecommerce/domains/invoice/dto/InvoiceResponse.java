package com.locnguyen.ecommerce.domains.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.invoice.enums.InvoiceStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Invoice with all data needed for rendering or printing")
public class InvoiceResponse {

    private final Long id;
    private final String invoiceCode;
    private final InvoiceStatus status;
    private final LocalDateTime issuedAt;
    private final LocalDate dueDate;
    private final String notes;

    private final Long orderId;
    private final String orderCode;
    private final PaymentMethod paymentMethod;
    private final PaymentStatus paymentStatus;
    private final LocalDateTime paidAt;

    private final String customerName;
    private final String customerEmail;
    private final String customerPhone;

    private final String billingStreet;
    private final String billingWard;
    private final String billingDistrict;
    private final String billingCity;
    private final String billingPostalCode;

    /** Populated for detail views; null in list views. */
    private final List<InvoiceItemResponse> items;

    private final BigDecimal subTotal;
    private final BigDecimal discountAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal totalAmount;
    private final String voucherCode;

    private final LocalDateTime createdAt;
}
