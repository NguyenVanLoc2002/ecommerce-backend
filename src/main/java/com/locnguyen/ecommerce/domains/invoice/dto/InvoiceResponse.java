package com.locnguyen.ecommerce.domains.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    // ─── Invoice metadata ────────────────────────────────────────────────────
    private final Long id;
    private final String invoiceCode;
    private final String status;
    private final LocalDateTime issuedAt;
    private final LocalDate dueDate;
    private final String notes;

    // ─── Order reference ─────────────────────────────────────────────────────
    private final Long orderId;
    private final String orderCode;
    private final String paymentMethod;
    private final String paymentStatus;
    private final LocalDateTime paidAt;

    // ─── Customer snapshot ───────────────────────────────────────────────────
    private final String customerName;
    private final String customerEmail;
    private final String customerPhone;

    // ─── Billing address snapshot ────────────────────────────────────────────
    private final String billingStreet;
    private final String billingWard;
    private final String billingDistrict;
    private final String billingCity;
    private final String billingPostalCode;

    // ─── Line items (from order snapshot) ────────────────────────────────────
    /** Populated for detail views; null in list views. */
    private final List<InvoiceItemResponse> items;

    // ─── Amounts ─────────────────────────────────────────────────────────────
    private final BigDecimal subTotal;
    private final BigDecimal discountAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal totalAmount;
    private final String voucherCode;

    private final LocalDateTime createdAt;
}
