package com.locnguyen.ecommerce.domains.carrier.calendar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

public record ShippingCalendar(Set<DayOfWeek> workdays, LocalTime opensAt, LocalTime closesAt,
                               LocalTime cutoffAt, Map<LocalDate, String> holidays) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public ShippingCalendar {
        workdays = Set.copyOf(workdays);
        holidays = Map.copyOf(holidays);
        if (workdays.isEmpty() || !opensAt.isBefore(closesAt)
                || !opensAt.isBefore(cutoffAt) || cutoffAt.isAfter(closesAt)) {
            throw new IllegalArgumentException("Invalid operating calendar days, hours or cut-off");
        }
    }

    public Instant processingStart(Instant eligibleAt) {
        ZonedDateTime eligible = eligibleAt.atZone(ZONE);
        LocalDate date = eligible.toLocalDate();
        LocalTime time = eligible.toLocalTime();
        if (!isWorkday(date) || !time.isBefore(cutoffAt)) {
            return nextWorkday(date).atTime(opensAt).atZone(ZONE).toInstant();
        }
        if (time.isBefore(opensAt)) {
            return date.atTime(opensAt).atZone(ZONE).toInstant();
        }
        return eligibleAt;
    }

    public LocalDate deliveryDate(LocalDate handoverDate, int businessDays) {
        if (businessDays < 0) {
            throw new IllegalArgumentException("Business days cannot be negative");
        }
        LocalDate date = handoverDate;
        for (int day = 0; day < businessDays; day++) {
            date = nextWorkday(date);
        }
        return date;
    }

    private LocalDate nextWorkday(LocalDate date) {
        do {
            date = date.plusDays(1);
        } while (!isWorkday(date));
        return date;
    }

    private boolean isWorkday(LocalDate date) {
        return workdays.contains(date.getDayOfWeek()) && !holidays.containsKey(date);
    }
}
