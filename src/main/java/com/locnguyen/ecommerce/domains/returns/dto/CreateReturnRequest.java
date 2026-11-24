package com.locnguyen.ecommerce.domains.returns.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateReturnRequest(
        @NotNull ReturnReason reason,
        @NotBlank @Size(max = 1000) String note,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items,
        boolean originalCondition,
        boolean unopened) {

    public CreateReturnRequest(ReturnReason reason, String note, List<Item> items) {
        this(reason, note, items, false, false);
    }

    public record Item(@NotNull UUID orderItemId, @Positive int quantity) {}
}
