package com.locnguyen.ecommerce.domains.carrier.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.calendar.ShippingCalendarModels.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.locnguyen.ecommerce.domains.carrier.calendar.ShippingCalendarTest.at;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class ShippingCalendarIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4");

    private static JdbcClient jdbc;
    private static TransactionTemplate tx;

    @BeforeAll
    static void migrate() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = JdbcClient.create(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Test
    void versionsCalendarWithAuditAndEnforcesSeparateProcessingAndDeliveryPurposes() {
        var access = mock(ShippingCalendarAccess.class);
        UUID operator = UUID.randomUUID();
        when(access.requireManager()).thenReturn(operator);
        var service = new ShippingCalendarService(jdbc, new ObjectMapper().findAndRegisterModules(), access);
        LocalDate holiday = LocalDate.parse("2026-11-30");
        var warehouse = input(Purpose.WAREHOUSE_PROCESSING, List.of(new Holiday(holiday, "Warehouse holiday")), true);
        var created = tx.execute(status -> service.create(warehouse));
        var shipping = tx.execute(status -> service.create(input(Purpose.CARRIER_TRANSPORT, List.of(), true)));
        assertThat(service.list(0).items()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(service.get(created.id()).calendar()).isEqualTo(warehouse);

        assertThat(service.previewProcessing(created.id(), new ProcessingInput(at("2026-11-28T15:00+07:00")))
                .processingStartsAt()).isEqualTo(at("2026-12-01T08:00+07:00"));
        assertThat(service.previewDelivery(shipping.id(), new DeliveryInput(LocalDate.parse("2026-11-28"), 1, 2)))
                .isEqualTo(new DeliveryWindow(LocalDate.parse("2026-11-29"), LocalDate.parse("2026-11-30")));
        assertThatThrownBy(() -> service.previewDelivery(created.id(), new DeliveryInput(holiday, 1, 2)))
                .isInstanceOf(AppException.class).hasMessageContaining("CARRIER_TRANSPORT");
        assertThatThrownBy(() -> service.previewDelivery(shipping.id(), new DeliveryInput(holiday, 2, 1)))
                .isInstanceOf(AppException.class).hasMessageContaining("maximum");

        var edited = tx.execute(status -> service.edit(created.id(), new Edit(created.revision(),
                input(Purpose.WAREHOUSE_PROCESSING, List.of(), false))));
        assertThat(edited.revision()).isEqualTo(2);
        assertThat(service.history(created.id(), 0).items()).extracting(Change::changedBy).containsExactly(operator, operator);
        assertThat(service.history(created.id(), 0).items().get(1).calendar()).isEqualTo(warehouse);
        assertThatThrownBy(() -> tx.execute(status -> service.edit(created.id(), new Edit(created.revision(), warehouse))))
                .isInstanceOf(AppException.class).hasMessageContaining("revision");
        assertThatThrownBy(() -> service.previewProcessing(created.id(), new ProcessingInput(at("2026-11-28T12:00+07:00"))))
                .isInstanceOf(AppException.class).hasMessageContaining("active");

        when(access.requireManager()).thenThrow(new AppException(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.list(0)).isInstanceOf(AppException.class);
    }

    private static Input input(Purpose purpose, List<Holiday> holidays, boolean active) {
        var days = purpose == Purpose.CARRIER_TRANSPORT ? EnumSet.allOf(DayOfWeek.class)
                : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
        return new Input("Test calendar", purpose, days, LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalTime.of(15, 0), holidays, active);
    }
}
