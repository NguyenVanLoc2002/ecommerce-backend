package com.locnguyen.ecommerce.domains.order.repository;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.UUID;
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.user WHERE o.id = :id")
    Optional<Order> findAdminByIdWithCurrentCustomer(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.user WHERE o.orderCode = :orderCode")
    Optional<Order> findAdminByOrderCodeWithCurrentCustomer(@Param("orderCode") String orderCode);

    Optional<Order> findByOrderCode(String orderCode);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE " +
            "(:customerId IS NULL OR o.customer.id = :customerId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "ORDER BY o.createdAt DESC")
    Page<Order> filter(@Param("customerId") UUID customerId,
                       @Param("status") com.locnguyen.ecommerce.domains.order.enums.OrderStatus status,
                       Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId " +
           "AND o.status = com.locnguyen.ecommerce.domains.order.enums.OrderStatus.COMPLETED")
    long countCompletedByCustomerId(@Param("customerId") UUID customerId);

    @Query(value = "SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.user u " +
            "WHERE (:customerId IS NULL OR o.customerId = :customerId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) " +
            "AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod) " +
            "AND (:createdFrom IS NULL OR o.createdAt >= :createdFrom) " +
            "AND (:createdTo IS NULL OR o.createdAt <= :createdTo) " +
            "AND (:keyword IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(COALESCE(o.customerNameSnapshot, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(COALESCE(o.customerEmailSnapshot, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:shipmentStatus IS NULL OR EXISTS (SELECT s.id FROM Shipment s WHERE s.order.id = o.id AND s.status = :shipmentStatus))",
            countQuery = "SELECT COUNT(o) FROM Order o LEFT JOIN o.customer c LEFT JOIN c.user u " +
                    "WHERE (:customerId IS NULL OR o.customerId = :customerId) " +
                    "AND (:status IS NULL OR o.status = :status) " +
                    "AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) " +
                    "AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod) " +
                    "AND (:createdFrom IS NULL OR o.createdAt >= :createdFrom) " +
                    "AND (:createdTo IS NULL OR o.createdAt <= :createdTo) " +
                    "AND (:keyword IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "OR LOWER(COALESCE(o.customerNameSnapshot, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "OR LOWER(COALESCE(o.customerEmailSnapshot, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "OR LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                    "AND (:shipmentStatus IS NULL OR EXISTS (SELECT s.id FROM Shipment s WHERE s.order.id = o.id AND s.status = :shipmentStatus))")
    Page<Order> adminSearch(@Param("customerId") UUID customerId,
                            @Param("status") OrderStatus status,
                            @Param("paymentStatus") PaymentStatus paymentStatus,
                            @Param("paymentMethod") PaymentMethod paymentMethod,
                            @Param("createdFrom") java.time.LocalDateTime createdFrom,
                            @Param("createdTo") java.time.LocalDateTime createdTo,
                            @Param("keyword") String keyword,
                            @Param("shipmentStatus") ShipmentStatus shipmentStatus,
                            Pageable pageable);
}
