package com.locnguyen.ecommerce.domains.productbadge.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.UUID;

public final class ProductBadgeModels {
    private ProductBadgeModels() {}

    public record Input(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,39}") String code,
            @NotBlank @Size(max = 60) String label,
            boolean active,
            @Min(0) @Max(1000000) int priority,
            @PositiveOrZero Long version
    ) {}
    public record Badge(UUID id, String code, String label, boolean active, int priority, long version) {}
    public record Display(String code, String label) implements Serializable {}
    public record ProductReference(UUID id, String name) {}
}
