package com.locnguyen.ecommerce.domains.returns.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import java.util.List;
import java.util.UUID;

public record CustomerReturnsResponse(List<EligibleItem> eligibleItems, List<ReturnResponse> requests) {
    public record EligibleItem(UUID orderItemId, int availableQuantity, List<ReturnReason> allowedReasons,
                               boolean unopenedRequiredForChangedMind) {}
}
