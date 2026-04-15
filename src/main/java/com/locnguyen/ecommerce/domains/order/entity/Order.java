package com.locnguyen.ecommerce.domains.order.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order entity — immutable after creation (no soft delete).
 *
 * <p>Shipping address is snapshotted at creation time so the order retains
 * the address that was selected at checkout, even if the customer modifies
 * or soft-deletes their address record later.
 *
 * <p>Extends {@link BaseEntity} (no soft delete) — orders are permanent records.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private Customer customer;

    @Column(name = "order_code", length = 50, nullable = false, unique = true)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    // ─── Shipping address snapshot ──────────────────────────────────────────

    @Column(name = "receiver_name", length = 100, nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", length = 20, nullable = false)
    private String receiverPhone;

    @Column(name = "shipping_street", length = 255, nullable = false)
    private String shippingStreet;

    @Column(name = "shipping_ward", length = 100, nullable = false)
    private String shippingWard;

    @Column(name = "shipping_district", length = 100, nullable = false)
    private String shippingDistrict;

    @Column(name = "shipping_city", length = 100, nullable = false)
    private String shippingCity;

    @Column(name = "shipping_postal_code", length = 20)
    private String shippingPostalCode;

    // ─── Pricing ────────────────────────────────────────────────────────────

    @Column(name = "sub_total", precision = 18, scale = 2, nullable = false)
    private BigDecimal subTotal;

    @Column(name = "discount_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    // ─── Payment ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50, nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 50, nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ─── Voucher ────────────────────────────────────────────────────────────

    @Column(name = "voucher_code", length = 100)
    private String voucherCode;

    // ─── Notes ──────────────────────────────────────────────────────────────

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    // ─── Items ──────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
    @ToString.Exclude
    private List<OrderItem> items = new ArrayList<>();
}
