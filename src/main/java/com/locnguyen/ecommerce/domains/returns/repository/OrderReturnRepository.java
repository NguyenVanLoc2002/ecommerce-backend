package com.locnguyen.ecommerce.domains.returns.repository;

import com.locnguyen.ecommerce.domains.returns.entity.OrderReturn;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {
    @Query("""
            select r from OrderReturn r
            where (:status is null or r.status = :status)
              and (:orderId is null or r.orderId = :orderId)
            """)
    Page<OrderReturn> findForAdmin(@Param("status") ReturnStatus status,
            @Param("orderId") UUID orderId, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    List<OrderReturn> findByOrderId(UUID orderId);
}
