package com.locnguyen.ecommerce.domains.returns.dto;

import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ExchangeStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerReturnDetail(ReturnResponse request, List<Event> timeline, Tracking tracking,
                                   List<InspectionLine> inspection, Refund refund, List<Exchange> exchanges) {
    public record Event(ReturnStatus status, LocalDateTime occurredAt) {}

    public record Tracking(String carrier, String trackingNumber, LocalDateTime trackingAt) {}

    public record InspectionLine(UUID orderItemId, int quantity, int restock, int damaged) {}

    public record Refund(BigDecimal amount, RefundStatus status, LocalDateTime createdAt, LocalDateTime refundedAt) {}

    public record Exchange(UUID id, UUID orderItemId, int quantity, ExchangeStatus status,
                           UUID replacementOrderId) {}
}
