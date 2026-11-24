package com.locnguyen.ecommerce.domains.returns.repository;

import com.locnguyen.ecommerce.domains.returns.entity.OrderReturn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {
    @EntityGraph(attributePaths = "lines")
    List<OrderReturn> findByOrderId(UUID orderId);
}
