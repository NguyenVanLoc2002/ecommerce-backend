package com.locnguyen.ecommerce.domains.sizeguide.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SizeGuideModels {
    private SizeGuideModels() {}

    public enum Measurement { BODY, GARMENT }
    public enum Unit { CM, IN, KG }
    public enum Scope { PRODUCT, CATEGORY_BRAND, BRAND, CATEGORY, SHOP }

    public record Column(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,31}") String key,
            @NotBlank @Size(max = 80) String label,
            @NotNull Unit unit
    ) implements Serializable {}

    public record Range(
            @NotNull @DecimalMin("0") @Digits(integer = 5, fraction = 3) BigDecimal min,
            @DecimalMin("0") @Digits(integer = 5, fraction = 3) BigDecimal max
    ) implements Serializable {}

    public record Row(
            @NotBlank @Size(max = 24) String size,
            @NotEmpty @Size(max = 12) Map<String, @NotNull @Valid Range> values
    ) implements Serializable {}

    public record Content(
            @NotNull Measurement measurement,
            @NotEmpty @Size(max = 12) List<@NotNull @Valid Column> columns,
            @NotEmpty @Size(max = 40) List<@NotNull @Valid Row> rows,
            @Size(max = 500) String measurementImageUrl,
            @Size(max = 1200) String notes
    ) implements Serializable {}

    public record Input(
            @NotBlank @Size(max = 120) String name,
            boolean active,
            @PositiveOrZero Long version,
            @Valid @NotNull Content content
    ) {}

    public record Summary(UUID id, String name, boolean active, long version) {}
    public record Guide(UUID id, String name, boolean active, long version, Content content) {}
    public record Display(UUID id, String name, Content content) implements Serializable {}

    public record AssignmentInput(
            @NotNull Scope scope, UUID productId, UUID categoryId, UUID brandId,
            @Min(0) @Max(1000000) int priority
    ) {}

    public record Assignment(String key, UUID guideId, Scope scope, UUID productId,
                             UUID categoryId, UUID brandId, int priority, String productName,
                             String categoryName, String brandName) {}
}
