package com.locnguyen.ecommerce.domains.promotion.dto;

import java.util.Set;
import java.util.UUID;

public record CheckoutVoucher(ValidateVoucherResponse quote, Set<UUID> eligibleVariantIds) {}
