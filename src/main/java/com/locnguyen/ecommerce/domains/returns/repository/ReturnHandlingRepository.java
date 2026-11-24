package com.locnguyen.ecommerce.domains.returns.repository;

import com.locnguyen.ecommerce.domains.returns.entity.ReturnHandling;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnHandlingRepository extends JpaRepository<ReturnHandling, UUID> {
    @EntityGraph(attributePaths = "items")
    Optional<ReturnHandling> findByReturnIdAndNextStatus(UUID returnId, ReturnStatus nextStatus);

    @EntityGraph(attributePaths = "items")
    List<ReturnHandling> findByReturnIdInAndNextStatus(List<UUID> returnIds, ReturnStatus nextStatus);
}
