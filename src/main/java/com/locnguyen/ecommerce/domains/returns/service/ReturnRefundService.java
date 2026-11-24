package com.locnguyen.ecommerce.domains.returns.service;

import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import java.util.UUID;

public interface ReturnRefundService {
    RefundResponse prepare(UUID orderId, UUID returnId);
}
