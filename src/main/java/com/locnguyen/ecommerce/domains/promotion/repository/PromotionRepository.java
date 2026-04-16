package com.locnguyen.ecommerce.domains.promotion.repository;

import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PromotionRepository extends JpaRepository<Promotion, Long>,
        JpaSpecificationExecutor<Promotion> {
}
