package com.locnguyen.ecommerce.domains.order.repository;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
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

    /**
     * Admin listing — eager-fetches customer + user to avoid N+1 on the response builder.
     * The separate countQuery avoids a Hibernate error when using JOIN FETCH with pagination.
     */
    @Query(
        value = "SELECT o FROM Order o " +
                "JOIN FETCH o.customer c JOIN FETCH c.user u " +
                "WHERE (:customerId IS NULL OR c.id = :customerId) " +
                "AND (:status IS NULL OR o.status = :status) " +
                "AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) " +
                "ORDER BY o.createdAt DESC",
        countQuery = "SELECT COUNT(o) FROM Order o " +
                "WHERE (:customerId IS NULL OR o.customer.id = :customerId) " +
                "AND (:status IS NULL OR o.status = :status) " +
                "AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)"
    )
    Page<Order> adminFilter(
            @Param("customerId") Long customerId,
            @Param("status") OrderStatus status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            Pageable pageable);
}
