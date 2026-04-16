package com.locnguyen.ecommerce.domains.invoice.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.invoice.enums.InvoiceStatus;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Invoice for an order — one invoice per order.
 *
 * <p>All customer and pricing data are snapshotted at generation time so the
 * invoice remains accurate even if the customer later updates their profile
 * or the order's denormalised fields change.
 *
 * <p>Extends {@link BaseEntity} (permanent record — no soft delete).
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "invoice_code", length = 50, nullable = false, unique = true)
    private String invoiceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    // ─── Customer snapshot ──────────────────────────────────────────────────

    @Column(name = "customer_name", length = 200, nullable = false)
    private String customerName;

    @Column(name = "customer_email", length = 255, nullable = false)
    private String customerEmail;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    // ─── Billing address snapshot ───────────────────────────────────────────

    @Column(name = "billing_street", length = 255, nullable = false)
    private String billingStreet;

    @Column(name = "billing_ward", length = 100, nullable = false)
    private String billingWard;

    @Column(name = "billing_district", length = 100, nullable = false)
    private String billingDistrict;

    @Column(name = "billing_city", length = 100, nullable = false)
    private String billingCity;

    @Column(name = "billing_postal_code", length = 20)
    private String billingPostalCode;

    // ─── Amounts snapshot ───────────────────────────────────────────────────

    @Column(name = "sub_total", precision = 18, scale = 2, nullable = false)
    private BigDecimal subTotal;

    @Column(name = "discount_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "voucher_code", length = 100)
    private String voucherCode;

    // ─── Dates ──────────────────────────────────────────────────────────────

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "notes", length = 1000)
    private String notes;
}
