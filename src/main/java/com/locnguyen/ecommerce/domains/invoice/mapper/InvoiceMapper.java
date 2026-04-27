package com.locnguyen.ecommerce.domains.invoice.mapper;

import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceItemResponse;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceResponse;
import com.locnguyen.ecommerce.domains.invoice.entity.Invoice;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    /**
     * Full response with line items — for detail / print views.
     */
    default InvoiceResponse toResponse(Invoice invoice) {
        if (invoice == null) return null;

        var order = invoice.getOrder();

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceCode(invoice.getInvoiceCode())
                .status(invoice.getStatus())
                .issuedAt(invoice.getIssuedAt())
                .dueDate(invoice.getDueDate())
                .notes(invoice.getNotes())
                // order reference
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .paidAt(order.getPaidAt())
                // customer snapshot
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getCustomerEmail())
                .customerPhone(invoice.getCustomerPhone())
                // billing address snapshot
                .billingStreet(invoice.getBillingStreet())
                .billingWard(invoice.getBillingWard())
                .billingDistrict(invoice.getBillingDistrict())
                .billingCity(invoice.getBillingCity())
                .billingPostalCode(invoice.getBillingPostalCode())
                // amounts
                .subTotal(invoice.getSubTotal())
                .discountAmount(invoice.getDiscountAmount())
                .shippingFee(invoice.getShippingFee())
                .totalAmount(invoice.getTotalAmount())
                .voucherCode(invoice.getVoucherCode())
                // line items from order snapshot
                .items(toItemResponses(order.getItems()))
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    /**
     * Lightweight response without line items — for paginated list views.
     */
    default InvoiceResponse toListItemResponse(Invoice invoice) {
        if (invoice == null) return null;

        var order = invoice.getOrder();

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceCode(invoice.getInvoiceCode())
                .status(invoice.getStatus())
                .issuedAt(invoice.getIssuedAt())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .customerName(invoice.getCustomerName())
                .subTotal(invoice.getSubTotal())
                .discountAmount(invoice.getDiscountAmount())
                .shippingFee(invoice.getShippingFee())
                .totalAmount(invoice.getTotalAmount())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    default InvoiceItemResponse toItemResponse(OrderItem item) {
        if (item == null) return null;

        var effectivePrice = item.getSalePrice() != null ? item.getSalePrice() : item.getUnitPrice();

        return InvoiceItemResponse.builder()
                .variantId(item.getVariant().getId())
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .sku(item.getSku())
                .unitPrice(item.getUnitPrice())
                .salePrice(item.getSalePrice())
                .effectivePrice(effectivePrice)
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .build();
    }

    List<InvoiceItemResponse> toItemResponses(List<OrderItem> items);
}
