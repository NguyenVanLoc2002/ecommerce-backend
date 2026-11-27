package com.locnguyen.ecommerce.domains.invoice.tax;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.order.service.tax.OrderTaxEstimates;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TaxInvoiceDocuments.Settings.class)
public class TaxInvoiceDocuments {
    @ConfigurationProperties(prefix = "app.tax.invoices")
    public record Settings(boolean recordingEnabled, Set<UUID> readers, Set<UUID> recorders) {
        public Settings {
            readers = readers == null ? Set.of() : Set.copyOf(readers);
            recorders = recorders == null ? Set.of() : Set.copyOf(recorders);
        }
    }

    public record InvoiceReference(UUID invoiceId, String invoiceCode, UUID orderId) {}

    public record ExternalEvidence(@NotBlank @Size(max = 100) String issuerReference,
                                   @NotBlank @Size(max = 40) String series,
                                   @NotBlank @Size(max = 80) String invoiceNumber,
                                   @NotBlank @Size(max = 160) String externalReference,
                                   @NotNull Instant issuedAt) {
        ExternalEvidence normalized() {
            return new ExternalEvidence(issuerReference.strip(), series.strip(), invoiceNumber.strip(),
                    externalReference.strip(), issuedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        }
    }

    public record ExternalRecord(ExternalEvidence evidence, Instant recordedAt, UUID recordedBy) {}

    public record Preparation(InvoiceReference invoice, OrderTaxEstimates.FinalSnapshot snapshot,
                              ExternalRecord externalRecord, boolean canRecord) {}

    private final JdbcClient jdbc;
    private final OrderTaxEstimates snapshots;
    private final Settings settings;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Preparation preparation(UUID invoiceId) {
        UUID operator = authorized();
        InvoiceReference invoice = invoice(invoiceId, false);
        return new Preparation(invoice, snapshots.getFinal(invoice.orderId()), recorded(invoiceId, false),
                settings.recordingEnabled() && settings.recorders().contains(operator));
    }

    @Transactional
    public ExternalRecord record(UUID invoiceId, ExternalEvidence request) {
        UUID operator = authorized();
        if (!settings.recorders().contains(operator) || !settings.recordingEnabled()) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        ExternalEvidence evidence = request.normalized();
        if (evidence.issuedAt().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Issued time must not be in the future");
        }
        InvoiceReference invoice = invoice(invoiceId, true);
        var existing = recorded(invoiceId, true);
        if (existing != null) {
            if (!existing.evidence().equals(evidence)) {
                throw new AppException(ErrorCode.CONFLICT, "External invoice evidence is already recorded");
            }
            return existing;
        }
        snapshots.getFinal(invoice.orderId());
        try {
            jdbc.sql("""
                    INSERT INTO external_tax_invoice_records
                    (invoice_id,issuer_reference,series,invoice_number,external_reference,issued_at,recorded_at,recorded_by)
                    VALUES (:invoice,:issuer,:series,:number,:external,:issued,UTC_TIMESTAMP(6),:actor)
                    """).param("invoice", invoiceId.toString()).param("issuer", evidence.issuerReference())
                    .param("series", evidence.series()).param("number", evidence.invoiceNumber())
                    .param("external", evidence.externalReference())
                    .param("issued", LocalDateTime.ofInstant(evidence.issuedAt(), ZoneOffset.UTC))
                    .param("actor", operator.toString()).update();
        } catch (DuplicateKeyException ex) {
            throw new AppException(ErrorCode.CONFLICT, "External invoice already recorded for this issuer and series");
        }
        return recorded(invoiceId, true);
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID invoiceId) {
        Preparation data = preparation(invoiceId);
        var amounts = data.snapshot().amounts();
        StringBuilder csv = new StringBuilder("invoice_code,order_id,tax_date,currency,line_key,category,treatment,rate_fraction,gross_before_discount,discount,gross,net,vat\r\n");
        for (var line : amounts.lines()) {
            List<String> values = List.of(data.invoice().invoiceCode(), data.invoice().orderId().toString(),
                    amounts.taxDate().toString(), amounts.currency(), line.lineKey(), line.categoryCode(),
                    line.treatment().name(), line.rateFraction().toPlainString(), line.beforeDiscount().toPlainString(),
                    line.discount().toPlainString(), line.gross().toPlainString(), line.net().toPlainString(),
                    line.vat().toPlainString());
            csv.append(String.join(",", values.stream().map(TaxInvoiceDocuments::csv).toList())).append("\r\n");
        }
        return csv.toString();
    }

    private InvoiceReference invoice(UUID id, boolean lock) {
        return jdbc.sql("SELECT id,invoice_code,order_id FROM invoices WHERE id=:invoice" + (lock ? " FOR UPDATE" : ""))
                .param("invoice", id.toString())
                .query((row, index) -> new InvoiceReference(id, row.getString("invoice_code"),
                        UUID.fromString(row.getString("order_id"))))
                .optional().orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
    }

    private ExternalRecord recorded(UUID id, boolean lock) {
        return jdbc.sql("SELECT * FROM external_tax_invoice_records WHERE invoice_id=:invoice" + (lock ? " FOR UPDATE" : ""))
                .param("invoice", id.toString())
                .query((row, index) -> new ExternalRecord(new ExternalEvidence(row.getString("issuer_reference"),
                        row.getString("series"), row.getString("invoice_number"), row.getString("external_reference"),
                        row.getObject("issued_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                        row.getObject("recorded_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        UUID.fromString(row.getString("recorded_by"))))
                .optional().orElse(null);
    }

    private UUID authorized() {
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return SecurityUtils.getCurrentUsername().flatMap(users::findByEmailAndDeletedFalse)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRoles().stream().anyMatch(role ->
                        role.getName() == RoleName.ADMIN || role.getName() == RoleName.SUPER_ADMIN))
                .map(User::getId)
                .filter(operator -> settings.readers().contains(operator) || settings.recorders().contains(operator))
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
    }

    static String csv(String value) {
        String trimmed = value.stripLeading();
        String safe = !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
