package com.locnguyen.ecommerce.domains.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnRefundAllocation(UUID orderItemId, int quantity, BigDecimal amount) {}
