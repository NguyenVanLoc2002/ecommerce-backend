package com.locnguyen.ecommerce.domains.order.service.tax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.service.tax.InclusiveVatCalculator.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderTaxEstimates {
    private static final ZoneId TAX_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String VND = "VND";
    private static final String SHIPPING_KEY = "shipping";

    private final TaxSettings settings;
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public record Estimate(Instant recordedAt, LocalDate taxDate, String currency, BigDecimal gross, BigDecimal net, BigDecimal vat,
                           List<SnapshotLine> lines) {}

    public record SnapshotLine(String lineKey, String categoryCode, int scheduleRevision,
                               LocalDate rateEffectiveFrom, Treatment treatment, BigDecimal rateFraction,
                               BigDecimal beforeDiscount, BigDecimal discount, BigDecimal gross,
                               BigDecimal net, BigDecimal vat) {}

    public record TransferEvidence(UUID eventId, Instant transferredAt, String reference) {}

    public record FinalSnapshot(TransferEvidence evidence, Estimate amounts) {}

    private record EffectiveRate(String code, Treatment treatment, int revision, BigDecimal fraction, LocalDate from) {}

    public boolean enabled() {
        return settings.orderEstimatesEnabled();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void capture(Order order) {
        capture(order, Instant.now());
    }

    void capture(Order order, Instant estimateTime) {
        LocalDate date = estimateTime.atZone(TAX_ZONE).toLocalDate();
        List<String> products = order.getItems().stream().map(OrderTaxEstimates::productId).distinct().toList();
        Map<String, String> overrides = assignments(products);
        Map<String, String> categoryByLine = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            categoryByLine.put(item.getId().toString(), overrides.getOrDefault(productId(item),
                    hasText(settings.defaultProductCategory()) ? settings.defaultProductCategory() : "STANDARD_REDUCIBLE"));
        }

        if (order.getShippingFee().signum() > 0) {
            if (!hasText(settings.shippingCategory())) {
                throw unavailable("Configure a separate shipping tax category");
            }
            categoryByLine.put(SHIPPING_KEY, settings.shippingCategory());
        }
        Map<String, EffectiveRate> rates = requireRates(categoryByLine, date);

        List<Line> amounts = new ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                String key = item.getId().toString();
                if (item.getVoucherDiscountAmount() == null || item.getNetLineAmount() == null
                        || item.getLineTotal().subtract(item.getVoucherDiscountAmount()).compareTo(item.getNetLineAmount()) != 0) {
                    throw unavailable("Missing or inconsistent merchandise discount snapshot");
                }
                amounts.add(line(key, rates.get(categoryByLine.get(key)), item.getLineTotal(), item.getVoucherDiscountAmount()));
            }
            if (categoryByLine.containsKey(SHIPPING_KEY)) {
                amounts.add(line(SHIPPING_KEY, rates.get(settings.shippingCategory()), order.getShippingFee(), BigDecimal.ZERO));
            }
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw unavailable("Tax estimates require internally consistent whole-VND prices");
        }
        Estimate estimate = allocate(estimateTime, date, amounts, categoryByLine, rates, order.getTotalAmount());
        insert(order.getId(), "ESTIMATE", date, estimate);
    }

    private Estimate allocate(Instant recordedAt, LocalDate date, List<Line> amounts, Map<String, String> categoryByLine,
                              Map<String, EffectiveRate> rates, BigDecimal orderGross) {
        var allocations = InclusiveVatCalculator.allocate(amounts);
        List<SnapshotLine> components = allocations.stream().map(allocation -> {
            Line input = allocation.line();
            Amounts result = allocation.amounts();
            EffectiveRate rate = rates.get(categoryByLine.get(input.key()));
            return new SnapshotLine(input.key(), rate.code(), rate.revision(), rate.from(), rate.treatment(),
                    rate.fraction(), input.gross(), input.discount(), result.gross(), result.net(), result.vat());
        }).toList();
        BigDecimal gross = components.stream().map(SnapshotLine::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = components.stream().map(SnapshotLine::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = components.stream().map(SnapshotLine::vat).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (gross.compareTo(orderGross) != 0 || gross.compareTo(net.add(vat)) != 0) {
            throw unavailable("Order totals do not reconcile with the VAT estimate");
        }
        return new Estimate(recordedAt, date, VND, gross, net, vat, components);
    }

    @Transactional(readOnly = true)
    public Estimate get(UUID orderId) {
        return read(orderId, "ESTIMATE").map(raw -> decode(raw, Estimate.class))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Tax estimate not available"));
    }

    /** Called by an approved transfer-of-rights integration with persisted event evidence. */
    @Transactional
    public FinalSnapshot captureFinal(UUID orderId, TransferEvidence evidence) {
        if (!settings.finalizationEnabled()) {
            throw unavailable("Final tax snapshots are disabled");
        }
        if (evidence == null || evidence.eventId() == null || evidence.transferredAt() == null
                || evidence.reference() == null || evidence.reference().isBlank()
                || evidence.reference().length() > 160 || evidence.transferredAt().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Valid transfer evidence is required");
        }
        jdbc.sql("SELECT id FROM orders WHERE id=:id FOR UPDATE")
                .param("id", orderId.toString()).query(String.class).optional()
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        var recorded = read(orderId, "FINAL", true).map(raw -> decode(raw, FinalSnapshot.class));
        if (recorded.isPresent()) {
            FinalSnapshot previous = recorded.get();
            if (!previous.evidence().equals(evidence)) {
                throw unavailable("The order already has a final tax snapshot for different evidence");
            }
            return previous;
        }
        Estimate estimate = read(orderId, "ESTIMATE", true).map(raw -> decode(raw, Estimate.class))
                .orElseThrow(() -> unavailable("A required order tax estimate is missing"));
        LocalDate date = evidence.transferredAt().atZone(TAX_ZONE).toLocalDate();
        if (evidence.transferredAt().isBefore(estimate.recordedAt())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Transfer date precedes the order tax estimate");
        }
        Map<String, String> categoryByLine = new HashMap<>();
        for (SnapshotLine input : estimate.lines()) {
            categoryByLine.put(input.lineKey(), input.categoryCode());
        }
        var rates = requireRates(categoryByLine, date);
        var amounts = estimate.lines().stream().map(input -> line(input.lineKey(), rates.get(input.categoryCode()),
                input.beforeDiscount(), input.discount())).toList();
        var snapshot = allocate(evidence.transferredAt(), date, amounts, categoryByLine, rates, estimate.gross());
        var finalized = new FinalSnapshot(evidence, snapshot);
        insert(orderId, "FINAL", date, finalized);
        return finalized;
    }

    @Transactional(readOnly = true)
    public FinalSnapshot getFinal(UUID orderId) {
        return read(orderId, "FINAL").map(raw -> decode(raw, FinalSnapshot.class))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Final tax snapshot not available"));
    }

    private Optional<String> read(UUID orderId, String phase) {
        return read(orderId, phase, false);
    }

    private Optional<String> read(UUID orderId, String phase, boolean lock) {
        return jdbc.sql("SELECT snapshot FROM order_tax_snapshots WHERE order_id=:id AND phase=:phase"
                        + (lock ? " FOR UPDATE" : ""))
                .param("id", orderId.toString()).param("phase", phase).query(String.class).optional();
    }

    private Map<String, EffectiveRate> requireRates(Map<String, String> categoryByLine, LocalDate date) {
        var codes = categoryByLine.values().stream().distinct().toList();
        Map<String, EffectiveRate> rates = effectiveRates(codes, date);
        if (rates.size() != codes.size()) {
            throw unavailable("A tax category is inactive or has no active rate on the tax date");
        }
        return rates;
    }

    private void insert(UUID orderId, String phase, LocalDate date, Object snapshot) {
        jdbc.sql("""
                INSERT INTO order_tax_snapshots (order_id,phase,tax_date,snapshot)
                VALUES (:id,:phase,:date,:snapshot)
                """).param("id", orderId.toString()).param("phase", phase).param("date", date)
                .param("snapshot", encode(snapshot)).update();
    }

    private Map<String, String> assignments(List<String> products) {
        Map<String, String> mapped = new HashMap<>();
        jdbc.sql("SELECT product_id, category_code FROM product_tax_categories WHERE product_id IN (:products)")
                .param("products", products)
                .query((row, index) -> Map.entry(row.getString("product_id"), row.getString("category_code")))
                .list().forEach(entry -> mapped.put(entry.getKey(), entry.getValue()));
        return mapped;
    }

    private Map<String, EffectiveRate> effectiveRates(List<String> codes, LocalDate date) {
        Map<String, EffectiveRate> mapped = new HashMap<>();
        var matches = jdbc.sql("""
                SELECT c.code,c.treatment,c.rate_revision,r.fraction,r.effective_from
                FROM tax_categories c JOIN tax_rates r ON r.category_code=c.code AND r.revision=c.rate_revision
                WHERE c.code IN (:codes) AND c.active=TRUE AND r.active=TRUE
                  AND r.effective_from<=:date AND (r.effective_until IS NULL OR r.effective_until>:date)
                """).param("codes", codes).param("date", date)
                .query((row, index) -> new EffectiveRate(row.getString("code"),
                        Treatment.valueOf(row.getString("treatment")), row.getInt("rate_revision"),
                        row.getBigDecimal("fraction"), row.getObject("effective_from", LocalDate.class))).list();
        for (EffectiveRate rate : matches) {
            if (mapped.putIfAbsent(rate.code(), rate) != null) {
                throw unavailable("Multiple active tax rates for category " + rate.code());
            }
        }
        return mapped;
    }

    private static Line line(String key, EffectiveRate rate, BigDecimal gross, BigDecimal discount) {
        return new Line(key, new Band(rate.treatment(), rate.fraction()), gross, discount);
    }

    private static String productId(OrderItem item) {
        return item.getVariant().getProduct().getId().toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize order tax estimate", ex);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read order tax estimate", ex);
        }
    }

    private static AppException unavailable(String reason) {
        return new AppException(ErrorCode.CONFLICT, reason);
    }
}
