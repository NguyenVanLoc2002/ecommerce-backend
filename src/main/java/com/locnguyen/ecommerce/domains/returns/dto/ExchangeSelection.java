package com.locnguyen.ecommerce.domains.returns.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ExchangeSelection(@NotNull UUID orderItemId, @NotNull UUID variantId,
                                @NotNull UUID warehouseId, @Positive int quantity,
                                @Size(max = 500) String historicalPriceEvidence) {}
