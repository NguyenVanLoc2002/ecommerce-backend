package com.locnguyen.ecommerce.domains.returns.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(UUID id, UUID orderId, ReturnStatus status, ReturnReason reason,
                             String note, LocalDateTime createdAt, List<CreateReturnRequest.Item> items) {}
