package com.locnguyen.ecommerce.common.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods for date/time operations.
 * All timestamps in the system are stored and returned in UTC.
 */
public final class DateTimeUtils {

    private DateTimeUtils() {}

    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC);
    }

    public static String nowIso() {
        return Instant.now().toString();
    }

    public static String toIso(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), UTC);
    }

    public static boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt != null && expiresAt.isBefore(nowUtc());
    }
}
