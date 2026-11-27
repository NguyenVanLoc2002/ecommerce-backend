package com.locnguyen.ecommerce.domains.carrier.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.calendar.ShippingCalendarModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingCalendarService {
    private static final int PAGE_SIZE = 20;
    private static final int HISTORY_SIZE = 10;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final ShippingCalendarAccess access;

    @Transactional(readOnly = true)
    public CalendarPage list(int page) {
        access.requireManager();
        var rows = jdbc.sql("""
                SELECT id, revision, name, purpose, active FROM shipping_calendars
                ORDER BY name, id LIMIT :limit OFFSET :offset
                """).param("offset", (long) page * PAGE_SIZE).param("limit", PAGE_SIZE + 1)
                .query((row, index) -> new Summary(UUID.fromString(row.getString("id")), row.getLong("revision"),
                        row.getString("name"), Purpose.valueOf(row.getString("purpose")), row.getBoolean("active")))
                .list();
        return new CalendarPage(rows.stream().limit(PAGE_SIZE).toList(), rows.size() > PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Calendar get(UUID id) {
        access.requireManager();
        return read(id);
    }

    @Transactional
    public Calendar create(Input input) {
        UUID actor = access.requireManager();
        ShippingCalendar validated = rules(input);
        UUID id = UUID.randomUUID();
        var insert = jdbc.sql("""
                INSERT INTO shipping_calendars
                    (id, name, purpose, workday_mask, opens_at, closes_at, cutoff_at, holidays, active)
                VALUES (:id,:name,:purpose,:days,:opens,:closes,:cutoff,:holidays,:active)
                """).param("id", id.toString()).param("purpose", input.purpose().name());
        bind(insert, input, validated).update();
        return recordRevision(read(id), actor);
    }

    @Transactional
    public Calendar edit(UUID id, Edit input) {
        UUID actor = access.requireManager();
        Input calendar = input.calendar();
        ShippingCalendar validated = rules(calendar);
        var edit = jdbc.sql("""
                UPDATE shipping_calendars SET revision = revision + 1, name = :name,
                    workday_mask = :days, opens_at = :opens, closes_at = :closes,
                    cutoff_at = :cutoff, holidays = :holidays, active = :active
                WHERE id = :id AND revision = :revision AND purpose = :purpose
                """).param("id", id.toString()).param("revision", input.revision())
                .param("purpose", calendar.purpose().name());
        if (bind(edit, calendar, validated).update() != 1) {
            read(id);
            throw new AppException(ErrorCode.CONFLICT, "Calendar revision or purpose has changed");
        }
        return recordRevision(read(id), actor);
    }

    @Transactional(readOnly = true)
    public HistoryPage history(UUID id, int page) {
        access.requireManager();
        var rows = jdbc.sql("""
                SELECT revision, changed_by, changed_at, snapshot FROM shipping_calendar_revisions
                WHERE calendar_id = :id ORDER BY revision DESC LIMIT :limit OFFSET :offset
                """).param("id", id.toString()).param("limit", HISTORY_SIZE + 1).param("offset", (long) page * HISTORY_SIZE)
                .query((row, index) -> new Change(row.getLong("revision"), UUID.fromString(row.getString("changed_by")),
                        row.getObject("changed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        decode(row.getString("snapshot"), new TypeReference<Input>() {})))
                .list();
        return new HistoryPage(rows.stream().limit(HISTORY_SIZE).toList(), rows.size() > HISTORY_SIZE);
    }

    @Transactional(readOnly = true)
    public ProcessingStart previewProcessing(UUID id, ProcessingInput request) {
        access.requireManager();
        return new ProcessingStart(activeCalendar(id, Purpose.WAREHOUSE_PROCESSING).processingStart(request.eligibleAt()));
    }

    @Transactional(readOnly = true)
    public DeliveryWindow previewDelivery(UUID id, DeliveryInput request) {
        access.requireManager();
        if (request.minBusinessDays() > request.maxBusinessDays()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "The maximum delivery days must be at least the minimum");
        }
        var calendar = activeCalendar(id, Purpose.CARRIER_TRANSPORT);
        return new DeliveryWindow(calendar.deliveryDate(request.handoverDate(), request.minBusinessDays()),
                calendar.deliveryDate(request.handoverDate(), request.maxBusinessDays()));
    }

    private ShippingCalendar activeCalendar(UUID id, Purpose required) {
        Input calendar = read(id).calendar();
        if (calendar.purpose() != required || !calendar.active()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Select an active " + required.name() + " calendar");
        }
        return rules(calendar);
    }

    private Calendar recordRevision(Calendar calendar, UUID actor) {
        jdbc.sql("""
                INSERT INTO shipping_calendar_revisions (calendar_id, revision, changed_by, changed_at, snapshot)
                VALUES (:id,:revision,:actor,UTC_TIMESTAMP(6),:snapshot)
                """).param("id", calendar.id().toString()).param("revision", calendar.revision())
                .param("actor", actor.toString()).param("snapshot", encode(calendar.calendar())).update();
        return calendar;
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec request, Input input, ShippingCalendar calendar) {
        int days = 0;
        for (DayOfWeek day : calendar.workdays()) {
            days |= 1 << (day.getValue() - 1);
        }
        return request.param("name", input.name().strip()).param("days", days)
                .param("opens", calendar.opensAt()).param("closes", calendar.closesAt())
                .param("cutoff", calendar.cutoffAt()).param("holidays", encode(input.holidays()))
                .param("active", input.active());
    }

    private Calendar read(UUID id) {
        return jdbc.sql("SELECT * FROM shipping_calendars WHERE id = :id").param("id", id.toString())
                .query(this::readRow).optional().orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    private Calendar readRow(ResultSet row, int index) throws SQLException {
        var days = EnumSet.noneOf(DayOfWeek.class);
        int mask = row.getInt("workday_mask");
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((mask & (1 << (day.getValue() - 1))) != 0) {
                days.add(day);
            }
        }
        Input input = new Input(row.getString("name"), Purpose.valueOf(row.getString("purpose")), days,
                row.getObject("opens_at", LocalTime.class), row.getObject("closes_at", LocalTime.class),
                row.getObject("cutoff_at", LocalTime.class), decode(row.getString("holidays"), new TypeReference<List<Holiday>>() {}),
                row.getBoolean("active"));
        return new Calendar(UUID.fromString(row.getString("id")), row.getLong("revision"), input,
                ShippingCalendar.ZONE.getId());
    }

    private static ShippingCalendar rules(Input input) {
        var holidays = new LinkedHashMap<LocalDate, String>();
        for (Holiday entry : input.holidays()) {
            if (holidays.putIfAbsent(entry.date(), entry.name().strip()) != null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "A holiday date can appear only once");
            }
        }
        if (input.opensAt().getNano() != 0 || input.closesAt().getNano() != 0 || input.cutoffAt().getNano() != 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Calendar times support seconds");
        }
        try {
            return new ShippingCalendar(input.workdays(), input.opensAt(), input.closesAt(), input.cutoffAt(), holidays);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize shipping calendar", ex);
        }
    }

    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read shipping calendar", ex);
        }
    }
}
