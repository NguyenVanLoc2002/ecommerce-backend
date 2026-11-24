package com.locnguyen.ecommerce.domains.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnFinancialLine(UUID id, int quantity, BigDecimal netLineAmount) {}
