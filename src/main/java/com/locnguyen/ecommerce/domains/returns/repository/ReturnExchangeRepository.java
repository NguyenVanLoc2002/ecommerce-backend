package com.locnguyen.ecommerce.domains.returns.repository;

import com.locnguyen.ecommerce.domains.returns.entity.ReturnExchange;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnExchangeRepository extends JpaRepository<ReturnExchange, UUID> {
    List<ReturnExchange> findByReturnId(UUID returnId);
}
