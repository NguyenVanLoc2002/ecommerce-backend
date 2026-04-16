package com.locnguyen.ecommerce.domains.order.repository;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE " +
            "(:customerId IS NULL OR o.customer.id = :customerId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "ORDER BY o.createdAt DESC")
    Page<Order> filter(@Param("customerId") Long customerId,
                       @Param("status") com.locnguyen.ecommerce.domains.order.enums.OrderStatus status,
                       Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId " +
           "AND o.status = com.locnguyen.ecommerce.domains.order.enums.OrderStatus.COMPLETED")
    long countCompletedByCustomerId(@Param("customerId") Long customerId);
}
