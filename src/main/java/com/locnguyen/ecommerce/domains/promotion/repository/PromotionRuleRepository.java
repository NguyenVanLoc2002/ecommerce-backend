package com.locnguyen.ecommerce.domains.promotion.repository;

import com.locnguyen.ecommerce.domains.promotion.entity.PromotionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRuleRepository extends JpaRepository<PromotionRule, Long> {

    List<PromotionRule> findByPromotionId(Long promotionId);

    void deleteByPromotionId(Long promotionId);
}
