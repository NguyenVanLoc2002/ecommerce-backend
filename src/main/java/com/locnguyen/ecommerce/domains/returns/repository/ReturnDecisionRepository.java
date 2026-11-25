package com.locnguyen.ecommerce.domains.returns.repository;

import com.locnguyen.ecommerce.domains.returns.entity.ReturnDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnDecisionRepository extends JpaRepository<ReturnDecision, UUID> {
    List<ReturnDecision> findAllByReturnIdOrderByCreatedAtAsc(UUID returnId);

    Optional<ReturnDecision> findByReturnIdAndRequestKey(UUID returnId, String requestKey);
}
