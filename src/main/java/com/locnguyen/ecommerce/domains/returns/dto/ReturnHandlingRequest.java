package com.locnguyen.ecommerce.domains.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReturnHandlingRequest(
        @NotBlank @Size(max = 1000) String note,
        @Size(max = 100) String carrier,
        @Size(max = 200) String trackingNumber,
        LocalDateTime trackingAt,
        @Size(max = 500) String evidenceReference,
        UUID warehouseId,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Line> items) {

    public record Line(@NotNull UUID orderItemId, @Positive int quantity,
                       @PositiveOrZero int restock, @PositiveOrZero int damaged) {}
}
