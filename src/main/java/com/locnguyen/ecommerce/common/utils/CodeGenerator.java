package com.locnguyen.ecommerce.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates human-readable business codes for transactional entities.
 *
 * <p>Format: {@code {PREFIX}{yyyyMMdd}{6-digit-random}}
 *
 * <p>Examples:
 * <pre>
 *   ORD20260408123456  — order code
 *   INV20260408789012  — invoice code
 *   SHP20260408345678  — shipment code
 *   TXN20260408901234  — payment transaction code
 *   VCH20260408567890  — voucher code (system-generated)
 * </pre>
 *
 * <p><strong>Note:</strong> Random suffix is sufficient for MVP. For high-throughput
 * production systems, replace with a database sequence or distributed ID (Snowflake/ULID).
 */
public final class CodeGenerator {

    private CodeGenerator() {}

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    public static String generateOrderCode() {
        return generate("ORD");
    }

    public static String generateInvoiceCode() {
        return generate("INV");
    }

    public static String generateShipmentCode() {
        return generate("SHP");
    }

    public static String generatePaymentCode() {
        return generate("PAY");
    }

    public static String generatePaymentTransactionCode() {
        return generate("TXN");
    }

    public static String generateRefundCode() {
        return generate("RFD");
    }

    public static String generateVoucherCode() {
        return generate("VCH");
    }

    /**
     * Generic generator with a custom prefix.
     *
     * @param prefix uppercase identifier prefix (e.g., "ORD")
     * @return unique-enough code for business use
     */
    public static String generate(String prefix) {
        String date = LocalDate.now().format(DATE_FORMAT);
        long random = ThreadLocalRandom.current().nextLong(100_000L, 999_999L);
        return prefix + date + random;
    }
}
