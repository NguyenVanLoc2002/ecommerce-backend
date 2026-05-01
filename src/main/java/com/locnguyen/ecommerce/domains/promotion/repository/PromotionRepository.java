package com.locnguyen.ecommerce.domains.promotion.repository;

import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
public interface PromotionRepository extends JpaRepository<Promotion, UUID>,
        JpaSpecificationExecutor<Promotion> {

    Optional<Promotion> findByIdAndDeletedFalse(UUID id);

    /**
     * Atomically increments the promotion usage counter.
     */
    @Modifying
    @Query("UPDATE Promotion p SET p.usageCount = p.usageCount + 1 WHERE p.id = :id")
    void incrementUsageCount(@Param("id") UUID id);

    /**
     * Atomically decrements the promotion usage counter, floored at zero.
     */
    @Modifying
    @Query("""
            UPDATE Promotion p
            SET p.usageCount = CASE WHEN p.usageCount > 0 THEN p.usageCount - 1 ELSE 0 END
            WHERE p.id = :id
            """)
    void decrementUsageCount(@Param("id") UUID id);
}
