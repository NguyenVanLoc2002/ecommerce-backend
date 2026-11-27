package com.locnguyen.ecommerce.domains.carrier.estimate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.addresscatalog.AddressCatalog;
import com.locnguyen.ecommerce.domains.carrier.estimate.DeliveryEstimateModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryEstimateRuleService {
    private static final int PAGE_SIZE = 20;
    private static final int HISTORY_SIZE = 10;
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final AddressCatalog addresses;
    private final DeliveryEstimateAccess access;

    @Transactional(readOnly = true)
    public RulePage list(UUID carrierId, int page) {
        access.requireManager();
        var items = jdbc.sql("""
                SELECT * FROM delivery_estimate_rules WHERE carrier_id=:carrier
                ORDER BY warehouse_id, service_code, province_code, ward_code, priority, effective_from DESC, id
                LIMIT :limit OFFSET :offset
                """).param("carrier", carrierId.toString()).param("limit", PAGE_SIZE + 1)
                .param("offset", (long) page * PAGE_SIZE).query(this::readRow).list();
        return new RulePage(items.stream().limit(PAGE_SIZE).toList(), items.size() > PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Rule get(UUID id) {
        access.requireManager();
        return read(id);
    }

    @Transactional
    public Rule create(Create request) {
        UUID actor = access.requireManager();
        Route route = normalize(request.route());
        Terms terms = request.terms();
        lockCarrier(route.carrierId());
        validate(route, terms, true);
        UUID id = UUID.randomUUID();
        validateOverlap(id, route, terms);
        var insert = jdbc.sql("""
                INSERT INTO delivery_estimate_rules
                (id, carrier_id, service_code, warehouse_id, province_code, ward_code,
                  calendar_id, min_business_days, max_business_days, priority, effective_from,
                  effective_until, active, guaranteed, coverage_reference)
                VALUES (:id,:carrier,:service,:warehouse,:province,:ward,:calendar,:minimum,:maximum,
                  :priority,:start,:end,:active,:guaranteed,:coverage)
                """).param("id", id.toString());
        bindTerms(bindRoute(insert, route), terms).update();
        return recordRevision(read(id), actor);
    }

    @Transactional
    public Rule edit(UUID id, Edit request) {
        UUID actor = access.requireManager();
        Rule existing = read(id);
        Terms terms = request.terms();
        Route route = existing.route();
        lockCarrier(route.carrierId());
        if (existing.revision() != request.revision()) {
            throw new AppException(ErrorCode.CONFLICT, "Delivery rule revision changed. Reload the rule.");
        }
        validate(route, terms, false);
        validateOverlap(id, route, terms);
        var update = jdbc.sql("""
                UPDATE delivery_estimate_rules
                SET revision=revision+1, calendar_id=:calendar, min_business_days=:minimum, max_business_days=:maximum,
                    priority=:priority, effective_from=:start, effective_until=:end, active=:active,
                    guaranteed=:guaranteed, coverage_reference=:coverage
                WHERE id=:id AND revision=:revision
                """).param("id", id.toString()).param("revision", request.revision());
        if (bindTerms(update, terms).update() != 1) {
            throw new AppException(ErrorCode.CONFLICT, "Delivery rule revision changed. Reload the rule.");
        }
        return recordRevision(read(id), actor);
    }

    @Transactional(readOnly = true)
    public HistoryPage history(UUID id, int page) {
        access.requireManager();
        var changes = jdbc.sql("""
                SELECT revision, changed_by, changed_at, snapshot FROM delivery_estimate_rule_revisions
                WHERE rule_id=:id ORDER BY revision DESC LIMIT :limit OFFSET :offset
                """).param("id", id.toString()).param("limit", HISTORY_SIZE + 1).param("offset", (long) page * HISTORY_SIZE)
                .query((row, index) -> new Change(row.getLong("revision"), uuid(row, "changed_by"),
                        row.getObject("changed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), decode(row.getString("snapshot")))).list();
        return new HistoryPage(changes.stream().limit(HISTORY_SIZE).toList(), changes.size() > HISTORY_SIZE);
    }

    @Transactional(readOnly = true)
    public List<CalendarOption> calendarOptions() {
        access.requireManager();
        return jdbc.sql("SELECT id, name FROM shipping_calendars WHERE active=TRUE AND purpose='CARRIER_TRANSPORT' ORDER BY name, id")
                .query((row, index) -> new CalendarOption(uuid(row, "id"), row.getString("name"))).list();
    }

    @Transactional(readOnly = true)
    public Optional<Estimate> preview(Preview request) {
        access.requireManager();
        Route destination = normalize(request.destination());
        validateDestination(destination);
        return bindRoute(jdbc.sql("""
                SELECT rule.id, rule.revision, rule.calendar_id, cal.revision AS calendar_revision,
                       rule.min_business_days, rule.max_business_days, rule.guaranteed
                FROM delivery_estimate_rules rule
                JOIN shipping_calendars cal ON cal.id=rule.calendar_id AND cal.active=TRUE AND cal.purpose='CARRIER_TRANSPORT'
                JOIN carriers carrier ON carrier.id=rule.carrier_id AND carrier.status='ACTIVE'
                JOIN warehouses warehouse ON warehouse.id=rule.warehouse_id AND warehouse.status='ACTIVE' AND warehouse.is_deleted=FALSE
                JOIN store_carrier_configs config ON config.carrier_id=rule.carrier_id AND config.enabled=TRUE
                       AND BINARY config.default_service_code=BINARY rule.service_code
                       AND (config.default_pickup_warehouse_id IS NULL OR config.default_pickup_warehouse_id=rule.warehouse_id)
                WHERE rule.carrier_id=:carrier AND rule.warehouse_id=:warehouse AND rule.service_code=:service
                  AND rule.province_code=:province AND rule.active=TRUE
                  AND (rule.ward_code=:ward OR rule.ward_code IS NULL)
                  AND rule.effective_from<=:date AND (rule.effective_until IS NULL OR rule.effective_until>:date)
                ORDER BY (rule.ward_code IS NULL), rule.priority, rule.id LIMIT 1
                """), destination).param("date", request.onDate())
                .query((row, index) -> new Estimate(uuid(row, "id"), row.getLong("revision"), uuid(row, "calendar_id"),
                        row.getLong("calendar_revision"), row.getInt("min_business_days"), row.getInt("max_business_days"),
                        row.getBoolean("guaranteed"), "POLICY", "CARRIER_HANDOVER")).optional();
    }

    private void lockCarrier(UUID id) {
        jdbc.sql("SELECT id FROM carriers WHERE id=:id FOR UPDATE")
                .param("id", id.toString()).query(String.class).optional()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Select a registered carrier"));
    }

    private void validate(Route route, Terms terms, boolean creating) {
        if (terms.minBusinessDays() > terms.maxBusinessDays()
                || (terms.effectiveUntil() != null && !terms.effectiveFrom().isBefore(terms.effectiveUntil()))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid days or effective date range");
        }
        if (creating || terms.active()) {
            validateDestination(route);
            jdbc.sql("SELECT id FROM warehouses WHERE id=:id AND status='ACTIVE' AND is_deleted=FALSE")
                    .param("id", route.warehouseId().toString()).query(String.class).optional()
                    .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Select an active warehouse"));
        }
        jdbc.sql("""
                SELECT id FROM shipping_calendars WHERE id=:id AND purpose='CARRIER_TRANSPORT'
                  AND (:draft=TRUE OR active=TRUE)
                """).param("id", terms.calendarId().toString()).param("draft", !terms.active())
                .query(String.class).optional().orElseThrow(() ->
                        new AppException(ErrorCode.VALIDATION_ERROR, "Select a valid carrier transport calendar"));
        if (!terms.active()) {
            return;
        }
        if (terms.coverageReference() == null || terms.coverageReference().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Verified service coverage reference is required to activate a rule");
        }
        jdbc.sql("""
                SELECT config.id FROM store_carrier_configs config
                JOIN carriers carrier ON carrier.id=config.carrier_id AND carrier.status='ACTIVE'
                WHERE config.carrier_id=:carrier AND config.enabled=TRUE AND BINARY config.default_service_code=BINARY :service
                  AND (config.default_pickup_warehouse_id IS NULL OR config.default_pickup_warehouse_id=:warehouse)
                """).param("carrier", route.carrierId().toString()).param("service", route.serviceCode())
                .param("warehouse", route.warehouseId().toString()).query(String.class).optional()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Service and warehouse must match the enabled carrier configuration"));
    }

    private void validateDestination(Route route) {
        if (route.wardCode() != null) {
            addresses.resolve(route.provinceCode(), route.wardCode());
            return;
        }
        jdbc.sql("SELECT code FROM address_provinces WHERE code=:code AND active=TRUE")
                .param("code", route.provinceCode()).query(String.class).optional()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Select an active province"));
    }

    private void validateOverlap(UUID id, Route route, Terms terms) {
        if (!terms.active()) {
            return;
        }
        var overlap = bindRoute(jdbc.sql("""
                SELECT id FROM delivery_estimate_rules
                WHERE id<>:id AND active=TRUE AND carrier_id=:carrier AND warehouse_id=:warehouse AND service_code=:service
                  AND province_code=:province AND ward_code <=> :ward AND priority=:priority
                  AND (effective_until IS NULL OR effective_until>:start) AND (:end IS NULL OR effective_from<:end)
                LIMIT 1 FOR UPDATE
                """), route).param("id", id.toString()).param("priority", terms.priority())
                .param("start", terms.effectiveFrom()).param("end", terms.effectiveUntil()).query(String.class).optional();
        if (overlap.isPresent()) {
            throw new AppException(ErrorCode.CONFLICT, "Active rules for the same destination and priority cannot overlap");
        }
    }

    private Rule recordRevision(Rule rule, UUID actor) {
        try {
            jdbc.sql("""
                    INSERT INTO delivery_estimate_rule_revisions (rule_id,revision,changed_by,changed_at,snapshot)
                    VALUES (:id,:revision,:actor,UTC_TIMESTAMP(6),:snapshot)
                    """).param("id", rule.id().toString()).param("revision", rule.revision())
                    .param("actor", actor.toString()).param("snapshot", json.writeValueAsString(rule)).update();
            return rule;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize delivery estimate rule", ex);
        }
    }

    private Rule read(UUID id) {
        return jdbc.sql("SELECT * FROM delivery_estimate_rules WHERE id=:id").param("id", id.toString())
                .query(this::readRow).optional().orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    private Rule readRow(ResultSet row, int index) throws SQLException {
        Route route = new Route(uuid(row, "carrier_id"), row.getString("service_code"), uuid(row, "warehouse_id"),
                row.getString("province_code"), row.getString("ward_code"));
        Terms terms = new Terms(uuid(row, "calendar_id"), row.getInt("min_business_days"), row.getInt("max_business_days"),
                row.getInt("priority"), row.getObject("effective_from", LocalDate.class), row.getObject("effective_until", LocalDate.class),
                row.getBoolean("active"), row.getBoolean("guaranteed"), row.getString("coverage_reference"));
        return new Rule(uuid(row, "id"), row.getLong("revision"), route, terms);
    }

    private Rule decode(String snapshot) {
        try {
            return json.readValue(snapshot, Rule.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize delivery estimate rule", ex);
        }
    }

    private static Route normalize(Route route) {
        return new Route(route.carrierId(), route.serviceCode().strip(), route.warehouseId(), route.provinceCode(), route.wardCode());
    }

    private static UUID uuid(ResultSet row, String column) throws SQLException {
        return UUID.fromString(row.getString(column));
    }

    private static JdbcClient.StatementSpec bindRoute(JdbcClient.StatementSpec statement, Route route) {
        return statement.param("carrier", route.carrierId().toString()).param("service", route.serviceCode())
                .param("warehouse", route.warehouseId().toString()).param("province", route.provinceCode())
                .param("ward", route.wardCode());
    }

    private static JdbcClient.StatementSpec bindTerms(JdbcClient.StatementSpec statement, Terms terms) {
        return statement.param("calendar", terms.calendarId().toString())
                .param("minimum", terms.minBusinessDays()).param("maximum", terms.maxBusinessDays())
                .param("priority", terms.priority()).param("start", terms.effectiveFrom()).param("end", terms.effectiveUntil())
                .param("active", terms.active()).param("guaranteed", terms.guaranteed())
                .param("coverage", terms.coverageReference() == null ? null : terms.coverageReference().strip());
    }
}
