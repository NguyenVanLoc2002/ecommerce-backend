package com.locnguyen.ecommerce.domains.returns.service;

import com.locnguyen.ecommerce.domains.returns.dto.ExchangeResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ExchangeSelection;
import java.util.List;
import java.util.UUID;

public interface ExchangeService {
    List<ExchangeResponse> listForReturn(UUID returnId);

    ExchangeResponse reserve(UUID orderId, UUID returnId, String key, String actor, ExchangeSelection selection);

    ExchangeResponse createReplacement(UUID orderId, UUID returnId, UUID exchangeId);

    ExchangeResponse cancel(UUID orderId, UUID returnId, UUID exchangeId, String actor);
}
