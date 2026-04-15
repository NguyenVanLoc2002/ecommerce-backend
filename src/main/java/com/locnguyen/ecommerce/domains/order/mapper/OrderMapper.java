package com.locnguyen.ecommerce.domains.order.mapper;

import com.locnguyen.ecommerce.domains.order.dto.OrderItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderItemResponse> toItemResponses(List<OrderItem> items);

    @Mapping(target = "totalItems", expression = "java(calculateTotalItems(order))")
    OrderListItemResponse toListItemResponse(Order order);

    List<OrderListItemResponse> toListItemResponses(List<Order> orders);

    default int calculateTotalItems(Order order) {
        if (order.getItems() == null) return 0;
        return order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
    }
}
