package com.locnguyen.ecommerce.domains.returns.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ExchangeStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExchangeResponse(UUID id, UUID returnId, UUID orderItemId, UUID variantId,
                               UUID warehouseId, int quantity, ExchangeStatus status, LocalDateTime expiresAt, UUID replacementOrderId) {}
