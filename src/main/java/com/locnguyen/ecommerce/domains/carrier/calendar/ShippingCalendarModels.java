package com.locnguyen.ecommerce.domains.carrier.calendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ShippingCalendarModels {
    private ShippingCalendarModels() {}

    public enum Purpose { WAREHOUSE_PROCESSING, CARRIER_TRANSPORT }

    public record Holiday(@NotNull LocalDate date, @NotBlank @Size(max = 150) String name) {}

    public record Input(@NotBlank @Size(max = 150) String name, @NotNull Purpose purpose,
                        @NotEmpty @Size(max = 7) Set<@NotNull DayOfWeek> workdays,
                        @NotNull LocalTime opensAt, @NotNull LocalTime closesAt, @NotNull LocalTime cutoffAt,
                        @NotNull @Size(max = 730) List<@NotNull @Valid Holiday> holidays,
                        @NotNull Boolean active) {}

    public record Edit(@NotNull @Min(1) Long revision, @NotNull @Valid Input calendar) {}

    public record Calendar(UUID id, long revision, Input calendar, String timeZone) {}

    public record Summary(UUID id, long revision, String name, Purpose purpose, boolean active) {}

    public record CalendarPage(List<Summary> items, boolean hasNext) {}

    public record ProcessingInput(@NotNull Instant eligibleAt) {}

    public record ProcessingStart(Instant processingStartsAt) {}

    public record DeliveryInput(@NotNull LocalDate handoverDate, @NotNull @Min(0) @Max(90) Integer minBusinessDays,
                                @NotNull @Min(0) @Max(90) Integer maxBusinessDays) {}

    public record DeliveryWindow(LocalDate earliest, LocalDate latest) {}

    public record Change(long revision, UUID changedBy, Instant changedAt, Input calendar) {}

    public record HistoryPage(List<Change> items, boolean hasNext) {}
}
