package com.locnguyen.ecommerce.domains.carrier.calendar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ShippingCalendarTest {
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);
    private static final LocalTime CUTOFF = LocalTime.of(15, 0);

    @Test
    void appliesWarehouseOpeningCutoffWeekendAndConfiguredHolidays() {
        var calendar = new ShippingCalendar(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
                OPEN, CLOSE, CUTOFF, Map.of(LocalDate.parse("2026-11-30"), "Holiday"));
        assertThat(calendar.processingStart(at("2026-11-27T07:59+07:00")))
                .isEqualTo(at("2026-11-27T08:00+07:00"));
        assertThat(calendar.processingStart(at("2026-11-27T14:59+07:00")))
                .isEqualTo(at("2026-11-27T14:59+07:00"));
        assertThat(calendar.processingStart(at("2026-11-27T15:00+07:00")))
                .isEqualTo(at("2026-11-28T08:00+07:00"));
        assertThat(calendar.processingStart(at("2026-11-28T15:00+07:00")))
                .isEqualTo(at("2026-12-01T08:00+07:00"));
        assertThat(calendar.processingStart(at("2026-11-29T11:00+07:00")))
                .isEqualTo(at("2026-12-01T08:00+07:00"));
    }

    @Test
    void countsCarrierWorkingDaysFromHandoverIndependentlyOfWarehouseHours() {
        var calendar = new ShippingCalendar(EnumSet.allOf(DayOfWeek.class), OPEN, CLOSE, CUTOFF,
                Map.of(LocalDate.parse("2026-11-30"), "Carrier holiday"));
        LocalDate handover = LocalDate.parse("2026-11-28");
        assertThat(calendar.deliveryDate(handover, 0)).isEqualTo(handover);
        assertThat(calendar.deliveryDate(handover, 1)).isEqualTo(LocalDate.parse("2026-11-29"));
        assertThat(calendar.deliveryDate(handover, 2)).isEqualTo(LocalDate.parse("2026-12-01"));
    }

    @Test
    void rejectsEmptyWorkingWeekAndCutoffOutsideHours() {
        assertThatThrownBy(() -> new ShippingCalendar(EnumSet.noneOf(DayOfWeek.class), OPEN, CLOSE, CUTOFF, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingCalendar(EnumSet.allOf(DayOfWeek.class), OPEN, CLOSE, LocalTime.of(18, 0), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Instant at(String value) {
        return OffsetDateTime.parse(value).toInstant();
    }
}
