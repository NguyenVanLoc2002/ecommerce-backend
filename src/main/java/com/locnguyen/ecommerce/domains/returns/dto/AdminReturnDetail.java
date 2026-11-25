package com.locnguyen.ecommerce.domains.returns.dto;

import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import java.util.List;
import java.util.UUID;

public record AdminReturnDetail(ReturnResponse request, Handling receipt, Handling inspection,
                                RefundResponse refund) {
    public record Handling(String note, String carrier, String trackingNumber, List<Line> items) {}

    public record Line(UUID orderItemId, int quantity, int restock, int damaged) {}
}
