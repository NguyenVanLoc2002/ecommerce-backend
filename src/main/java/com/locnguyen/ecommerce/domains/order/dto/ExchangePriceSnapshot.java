package com.locnguyen.ecommerce.domains.order.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ExchangePriceSnapshot(BigDecimal purchasedBasePrice, Map<UUID, BigDecimal> variantBasePrices) {}
