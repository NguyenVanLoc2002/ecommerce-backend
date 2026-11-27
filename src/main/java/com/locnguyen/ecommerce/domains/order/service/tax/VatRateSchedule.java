package com.locnguyen.ecommerce.domains.order.service.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VatRateSchedule {
    private final Map<String, List<Rate>> ratesByGroup;

    public VatRateSchedule(List<Rate> configuredRates) {
        var byGroup = configuredRates.stream()
                .collect(Collectors.groupingBy(Rate::groupCode));
        byGroup.replaceAll((group, rates) -> validateGroup(group, rates));
        ratesByGroup = Map.copyOf(byGroup);
    }

    public Optional<Rate> resolve(String groupCode, LocalDate taxDate) {
        Objects.requireNonNull(groupCode, "VAT group code is required");
        Objects.requireNonNull(taxDate, "VAT tax date is required");
        return ratesByGroup.getOrDefault(groupCode, List.of()).stream()
                .filter(rate -> rate.appliesOn(taxDate))
                .findFirst();
    }

    private static List<Rate> validateGroup(String group, List<Rate> rates) {
        List<Rate> sorted = rates.stream().sorted(Comparator.comparing(Rate::effectiveFrom)).toList();
        for (int index = 1; index < sorted.size(); index++) {
            LocalDate previousEnd = sorted.get(index - 1).effectiveUntil();
            if (previousEnd == null || sorted.get(index).effectiveFrom().isBefore(previousEnd)) {
                throw new IllegalArgumentException("Overlapping VAT periods for group " + group);
            }
        }
        return sorted;
    }

    /** A configured rate fraction such as 0.08. The end date is exclusive. */
    public record Rate(String groupCode, BigDecimal fraction, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        public Rate {
            if (groupCode == null || groupCode.isBlank()) {
                throw new IllegalArgumentException("VAT group code is required");
            }
            groupCode = groupCode.strip();
            Objects.requireNonNull(fraction, "VAT rate is required");
            Objects.requireNonNull(effectiveFrom, "VAT start date is required");
            if (fraction.signum() < 0 || fraction.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("VAT rate fraction must be between 0 and 1");
            }
            if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
                throw new IllegalArgumentException("VAT end date must follow its start date");
            }
        }

        public boolean appliesOn(LocalDate taxDate) {
            return !taxDate.isBefore(effectiveFrom) && (effectiveUntil == null || taxDate.isBefore(effectiveUntil));
        }
    }
}
