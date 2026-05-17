package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.admin.dto.AdminOrderListItemResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.OrderAdminFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderPreviewResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderPreviewResponse previewOrder(Customer customer, CreateOrderRequest request);

    OrderResponse createOrder(Customer customer, CreateOrderRequest request, String idempotencyKey);

    PagedResponse<OrderListItemResponse> getMyOrders(Customer customer, OrderFilter filter, Pageable pageable);

    OrderResponse getOrderById(UUID orderId, Customer customer);

    OrderResponse getOrderByCode(String orderCode);

    PagedResponse<AdminOrderListItemResponse> getAllOrders(OrderAdminFilter filter, Pageable pageable);

    OrderResponse getOrderByIdAdmin(UUID orderId);

    OrderResponse confirmOrder(UUID orderId);

    OrderResponse cancelOrder(UUID orderId);

    OrderResponse cancelMyOrder(UUID orderId, Customer customer);

    OrderResponse completeOrder(UUID orderId);

    OrderResponse processOrder(UUID orderId);

    OrderResponse deliverOrder(UUID orderId);
}
