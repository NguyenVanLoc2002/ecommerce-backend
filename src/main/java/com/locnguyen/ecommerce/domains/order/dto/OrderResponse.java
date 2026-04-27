package com.locnguyen.ecommerce.domains.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Order response — full order with items")
public class OrderResponse {

    private final Long id;
    private final String orderCode;
    private final Long customerId;
    private final OrderStatus status;
    private final PaymentMethod paymentMethod;
    private final PaymentStatus paymentStatus;

    // Shipping address
    private final String receiverName;
    private final String receiverPhone;
    private final String shippingStreet;
    private final String shippingWard;
    private final String shippingDistrict;
    private final String shippingCity;
    private final String shippingPostalCode;

    // Pricing
    private final BigDecimal subTotal;
    private final BigDecimal discountAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal totalAmount;

    // Voucher
    private final String voucherCode;

    // Notes
    private final String customerNote;

    // Items
    private final List<OrderItemResponse> items;

    // Timestamps
    private final LocalDateTime createdAt;
}
