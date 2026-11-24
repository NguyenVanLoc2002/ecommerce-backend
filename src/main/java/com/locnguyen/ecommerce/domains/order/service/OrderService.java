package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.admin.dto.AdminOrderListItemResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.CancellationOrderContext;
import com.locnguyen.ecommerce.domains.order.dto.OrderAdminFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderPreviewResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.ValidateVoucherResponse;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

import com.locnguyen.ecommerce.domains.order.dto.ExchangePriceSnapshot;
import com.locnguyen.ecommerce.domains.order.dto.ReturnFinancialLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderService {

    ValidateVoucherResponse quoteCartVoucher(Customer customer, String voucherCode);

    OrderPreviewResponse previewOrder(Customer customer, CreateOrderRequest request);

    OrderResponse createOrder(Customer customer, CreateOrderRequest request, String idempotencyKey);

    PagedResponse<OrderListItemResponse> getMyOrders(Customer customer, OrderFilter filter, Pageable pageable);

    OrderResponse getOrderById(UUID orderId, Customer customer);

    Optional<UUID> createExchangeReplacement(UUID orderId, UUID itemId, UUID exchangeId, UUID variantId, int quantity);

    List<ReturnFinancialLine> getReturnFinancialLines(UUID orderId);

    ExchangePriceSnapshot getExchangePriceSnapshot(UUID orderId, UUID itemId);

    OrderStatus lockOrderForReturn(UUID orderId);

    OrderResponse lockOwnedOrderForReturn(UUID orderId, Customer customer);

    OrderResponse getOrderByCode(String orderCode);

    PagedResponse<AdminOrderListItemResponse> getAllOrders(OrderAdminFilter filter, Pageable pageable);

    OrderResponse getOrderByIdAdmin(UUID orderId);

    OrderResponse confirmOrder(UUID orderId);

    OrderResponse cancelOrder(UUID orderId);

    OrderResponse cancelMyOrder(UUID orderId, Customer customer);

    OrderResponse completeOrder(UUID orderId);

    OrderResponse processOrder(UUID orderId);

    OrderResponse deliverOrder(UUID orderId);

    CancellationOrderContext getCancellationContext(UUID orderId);

    CancellationOrderContext lockCancellationOrder(UUID orderId);

    CancellationOrderContext finalizeCancellation(UUID orderId, String actor, String reason);
}
