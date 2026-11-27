package com.locnguyen.ecommerce.domains.carrier.estimate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DeliveryEstimateModels {
    private DeliveryEstimateModels() {}

    public record Route(@NotNull UUID carrierId, @NotBlank @Size(max = 100) String serviceCode,
                        @NotNull UUID warehouseId,
                        @NotNull @Pattern(regexp = "[0-9]{2}") String provinceCode,
                        @Pattern(regexp = "[0-9]{5}") String wardCode) {}

    public record Terms(@NotNull UUID calendarId, @NotNull @Min(0) @Max(90) Integer minBusinessDays,
                        @NotNull @Min(0) @Max(90) Integer maxBusinessDays,
                        @NotNull @Min(0) @Max(100_000) Integer priority,
                        @NotNull LocalDate effectiveFrom, LocalDate effectiveUntil,
                        @NotNull Boolean active, @NotNull Boolean guaranteed,
                        @Size(max = 200) String coverageReference) {}

    public record Create(@NotNull @Valid Route route, @NotNull @Valid Terms terms) {}

    public record Edit(@NotNull @Min(1) Long revision, @NotNull @Valid Terms terms) {}

    public record Rule(UUID id, long revision, Route route, Terms terms) {}

    public record Change(long revision, UUID changedBy, Instant changedAt, Rule rule) {}

    public record CalendarOption(UUID id, String name) {}

    public record RulePage(List<Rule> items, boolean hasNext) {}

    public record HistoryPage(List<Change> items, boolean hasNext) {}

    public record Preview(@NotNull @Valid Route destination, @NotNull LocalDate onDate) {}

    public record EstimateResult(Estimate estimate) {}

    public record Estimate(UUID ruleId, long ruleRevision, UUID calendarId, long calendarRevision,
                           int minBusinessDays, int maxBusinessDays, boolean guaranteed,
                           String source, String referenceEvent) {}
}
