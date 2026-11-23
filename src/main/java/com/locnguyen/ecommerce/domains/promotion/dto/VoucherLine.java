package com.locnguyen.ecommerce.domains.promotion.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record VoucherLine(UUID variantId, UUID productId, Set<UUID> categoryIds,
                          UUID brandId, BigDecimal lineTotal) {}
