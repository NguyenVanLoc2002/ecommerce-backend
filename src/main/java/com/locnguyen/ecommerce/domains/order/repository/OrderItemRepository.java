package com.locnguyen.ecommerce.domains.order.repository;

import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.UUID;
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @EntityGraph(attributePaths = {"variant", "variant.product", "variant.product.brand"})
    List<OrderItem> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    @EntityGraph(attributePaths = {"variant", "variant.product", "variant.product.brand"})
    List<OrderItem> findByOrderIdInOrderByCreatedAtAscIdAsc(List<UUID> orderIds);

    @Query("SELECT oi.order.id AS orderId, SUM(oi.quantity) AS totalQuantity " +
            "FROM OrderItem oi WHERE oi.order.id IN :orderIds GROUP BY oi.order.id")
    List<OrderItemCountProjection> countQuantitiesByOrderIds(@Param("orderIds") List<UUID> orderIds);
}
