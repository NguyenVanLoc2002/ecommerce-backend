package com.locnguyen.ecommerce.domains.notification.repository;

import com.locnguyen.ecommerce.domains.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    long countByCustomerIdAndReadFalse(Long customerId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.customer.id = :customerId AND n.read = false")
    int markAllReadByCustomerId(@Param("customerId") Long customerId, @Param("readAt") LocalDateTime readAt);
}
