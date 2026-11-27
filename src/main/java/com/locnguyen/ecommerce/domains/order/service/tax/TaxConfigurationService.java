package com.locnguyen.ecommerce.domains.order.service.tax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.order.service.tax.InclusiveVatCalculator.Treatment;
import com.locnguyen.ecommerce.domains.order.service.tax.TaxInputs.*;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TaxSettings.class)
public class TaxConfigurationService {
    private final JdbcClient jdbc;
    private final TaxSettings settings;
    private final UserRepository users;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public UUID requireConfigurator() {
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return SecurityUtils.getCurrentUsername().flatMap(users::findByEmailAndDeletedFalse)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRoles().stream().anyMatch(role ->
                        role.getName() == RoleName.ADMIN || role.getName() == RoleName.SUPER_ADMIN))
                .map(User::getId)
                .filter(settings.configurators()::contains)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        requireConfigurator();
        return jdbc.sql("SELECT * FROM tax_categories ORDER BY code").query(TaxConfigurationService::category).list();
    }

    @Transactional(readOnly = true)
    public Schedule get(String code) {
        requireConfigurator();
        return readSchedule(loadCategory(code, false));
    }

    @Transactional
    public Category create(CategoryInput input) {
        UUID actor = requireConfigurator();
        try {
            jdbc.sql("""
                    INSERT INTO tax_categories (code, name, treatment, created_by, updated_by)
                    VALUES (:code, :name, :treatment, :actor, :actor)
                    """)
                    .param("code", input.code())
                    .param("name", input.name().strip())
                    .param("treatment", input.treatment().name())
                    .param("actor", actor.toString())
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new AppException(ErrorCode.CONFLICT, "Tax category code already exists");
        }
        audit("CATEGORY_CREATED", input.code(), actor, input);
        return loadCategory(input.code(), false);
    }

    @Transactional
    public Category update(String code, CategoryUpdate input) {
        UUID actor = requireConfigurator();
        loadCategory(code, true);
        jdbc.sql("""
                UPDATE tax_categories SET name=:name,active=:active,
                    updated_by=:actor,updated_at=UTC_TIMESTAMP(6) WHERE code=:code
                """)
                .param("code", code)
                .param("name", input.name().strip())
                .param("active", input.active())
                .param("actor", actor.toString())
                .update();
        audit("CATEGORY_UPDATED", code, actor, input);
        return loadCategory(code, false);
    }

    @Transactional
    public Schedule configure(String code, ScheduleUpdate input) {
        UUID actor = requireConfigurator();
        Category category = loadCategory(code, true);
        if (category.revision() != input.revision()) {
            throw new AppException(ErrorCode.CONFLICT, "Tax schedule changed. Reload the category.");
        }
        validateSchedule(category, input.rates());
        int revision = category.revision() + 1;
        jdbc.sql("INSERT INTO tax_rate_schedules (category_code, revision, created_by) VALUES (:code,:rev,:actor)")
                .param("code", code).param("rev", revision).param("actor", actor.toString()).update();
        for (RateInput rate : input.rates()) {
            jdbc.sql("""
                    INSERT INTO tax_rates (category_code,revision,fraction,effective_from,effective_until,active)
                    VALUES (:code,:rev,:fraction,:start,:end,:active)
                    """)
                    .param("code", code).param("rev", revision).param("fraction", rate.fraction())
                    .param("start", rate.effectiveFrom()).param("end", rate.effectiveUntil()).param("active", rate.active())
                    .update();
        }
        jdbc.sql("""
                UPDATE tax_categories SET rate_revision=:rev,updated_by=:actor,updated_at=UTC_TIMESTAMP(6)
                WHERE code=:code
                """).param("code", code).param("rev", revision).param("actor", actor.toString()).update();
        audit("SCHEDULE_CONFIGURED", code, actor, input);
        return readSchedule(loadCategory(code, false));
    }

    @Transactional(readOnly = true)
    public Assignment assignment(UUID productId) {
        requireConfigurator();
        return new Assignment(jdbc.sql("SELECT category_code FROM product_tax_categories WHERE product_id=:id")
                .param("id", productId.toString()).query(String.class).optional().orElse(null));
    }

    @Transactional
    public Assignment assign(UUID productId, Assignment input) {
        UUID actor = requireConfigurator();
        jdbc.sql("SELECT id FROM products WHERE id=:id AND is_deleted=FALSE FOR UPDATE")
                .param("id", productId.toString()).query(String.class).optional()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (input.categoryCode() == null) {
            jdbc.sql("DELETE FROM product_tax_categories WHERE product_id=:id").param("id", productId.toString()).update();
        } else {
            Category category = loadCategory(input.categoryCode(), true);
            if (!category.active()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Assign an active tax category");
            }
            jdbc.sql("""
                    INSERT INTO product_tax_categories (product_id,category_code) VALUES (:id,:code)
                    ON DUPLICATE KEY UPDATE category_code=VALUES(category_code)
                    """).param("id", productId.toString()).param("code", category.code()).update();
        }
        audit("PRODUCT_ASSIGNED", productId.toString(), actor, input);
        return input;
    }

    @Transactional(readOnly = true)
    public List<ConfigAudit> audit(String subject, long before) {
        requireConfigurator();
        return jdbc.sql("""
                SELECT id,event,actor,details,changed_at FROM tax_configuration_audit
                WHERE subject=:subject AND id < :before ORDER BY id DESC LIMIT 20
                """).param("subject", subject).param("before", before == 0 ? Long.MAX_VALUE : before)
                .query((row, index) -> new ConfigAudit(row.getLong("id"), row.getString("event"),
                        row.getString("actor"), row.getString("details"),
                        row.getObject("changed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC).toString()))
                .list();
    }

    private Category loadCategory(String code, boolean lock) {
        return jdbc.sql("SELECT * FROM tax_categories WHERE code=:code" + (lock ? " FOR UPDATE" : ""))
                .param("code", code).query(TaxConfigurationService::category).optional()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Unknown tax category"));
    }

    private Schedule readSchedule(Category category) {
        List<RateInput> rates = jdbc.sql("""
                SELECT fraction,effective_from,effective_until,active
                FROM tax_rates WHERE category_code=:code AND revision=:rev ORDER BY effective_from
                """).param("code", category.code()).param("rev", category.revision())
                .query((row, index) -> new RateInput(row.getBigDecimal("fraction"), row.getObject("effective_from", LocalDate.class),
                        row.getObject("effective_until", LocalDate.class), row.getBoolean("active")))
                .list();
        return new Schedule(category, rates);
    }

    private static void validateSchedule(Category category, List<RateInput> rates) {
        try {
            if (rates.stream().map(RateInput::effectiveFrom).distinct().count() != rates.size()) {
                throw new IllegalArgumentException("Every start date must be unique");
            }
            if ((category.treatment() == Treatment.NON_TAXABLE || "ZERO_RATE".equals(category.code()))
                    && rates.stream().anyMatch(rate -> rate.fraction().signum() != 0)) {
                throw new IllegalArgumentException("This tax category requires a zero fraction");
            }
            var definitions = rates.stream().map(rate -> new VatRateSchedule.Rate(category.code(), rate.fraction(),
                    rate.effectiveFrom(), rate.effectiveUntil())).toList();
            new VatRateSchedule(definitions.stream().filter(rate -> rates.stream()
                    .anyMatch(input -> input.active() && input.effectiveFrom().equals(rate.effectiveFrom()))).toList());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private void audit(String event, String subject, UUID actor, Object details) {
        try {
            jdbc.sql("""
                    INSERT INTO tax_configuration_audit (event,subject,actor,details,changed_at)
                    VALUES (:event,:subject,:actor,:details,UTC_TIMESTAMP(6))
                    """).param("event", event).param("subject", subject).param("actor", actor.toString())
                    .param("details", json.writeValueAsString(details)).update();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize tax configuration audit", ex);
        }
    }

    private static Category category(ResultSet row, int index) throws SQLException {
        return new Category(row.getString("code"), row.getString("name"),
                Treatment.valueOf(row.getString("treatment")), row.getBoolean("active"), row.getInt("rate_revision"));
    }
}
