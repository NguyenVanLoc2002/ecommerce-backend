package com.locnguyen.ecommerce.domains.carrier.estimate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.addresscatalog.AddressCatalog;
import com.locnguyen.ecommerce.domains.carrier.estimate.DeliveryEstimateModels.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class DeliveryEstimateRuleIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4");

    private static final LocalDate START = LocalDate.parse("2026-11-01");
    private static final LocalDate END = LocalDate.parse("2026-12-01");
    private static JdbcTemplate jdbc;
    private static JdbcClient client;
    private static TransactionTemplate tx;

    @BeforeAll
    static void migrate() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        client = JdbcClient.create(jdbc);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("INSERT INTO address_provinces (code,name) VALUES ('01','Province 1'),('79','Province 2')");
        jdbc.update("INSERT INTO address_wards (code,province_code,name) VALUES ('00001','01','Ward 1'),('26734','79','Ward 2')");
    }

    @Test
    void ranksWardThenPriorityAndFallsBackToProvinceOnlyWhenEffective() {
        Fixture fixture = fixture();
        var province = tx.execute(status -> fixture.service.create(new Create(fixture.route(null), fixture.terms(1, 4, 1))));
        var exact = fixture.route("00001");
        tx.execute(status -> fixture.service.create(new Create(exact, fixture.terms(2, 3, 9))));
        var strongest = tx.execute(status -> fixture.service.create(new Create(exact, fixture.terms(1, 2, 4))));
        var selected = fixture.service.preview(new Preview(exact, START)).orElseThrow();
        assertThat(selected.ruleId()).isEqualTo(strongest.id());
        assertThat(selected.source()).isEqualTo("POLICY");
        assertThat(selected.referenceEvent()).isEqualTo("CARRIER_HANDOVER");
        assertThat(fixture.service.preview(new Preview(exact, END))).isEmpty();
        assertThat(fixture.service.preview(new Preview(fixture.route(null), START)).orElseThrow().ruleId())
                .isEqualTo(province.id());

        assertThatThrownBy(() -> tx.execute(status -> fixture.service.create(
                new Create(exact, fixture.terms(0, 2, 4)))))
                .isInstanceOf(AppException.class).hasMessageContaining("overlap");
        assertThatThrownBy(() -> tx.execute(status -> fixture.service.create(new Create(fixture.route("26734"), fixture.terms(1, 2, 4)))))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> tx.execute(status -> fixture.service.create(new Create(exact, fixture.terms(3, 1, 8)))))
                .isInstanceOf(AppException.class).hasMessageContaining("range");

        jdbc.update("UPDATE shipping_calendars SET active=FALSE WHERE id=?", fixture.calendar.toString());
        assertThat(fixture.service.preview(new Preview(exact, START))).isEmpty();
        var disabled = new Terms(fixture.calendar, 1, 2, 4, START, END, false, false, null);
        var edited = tx.execute(status -> fixture.service.edit(strongest.id(), new Edit(strongest.revision(), disabled)));
        assertThat(edited.revision()).isEqualTo(2);
        assertThat(fixture.service.history(strongest.id(), 0).items()).extracting(Change::revision).containsExactly(2L,1L);
    }

    @Test
    void serializesConcurrentInsertionsForTheSameActivePriority() throws Exception {
        Fixture fixture = fixture();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> create = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal missing");
                }
                try {
                    tx.execute(status -> fixture.service.create(new Create(fixture.route(null), fixture.terms(1, 2, 2))));
                    return true;
                } catch (AppException conflict) {
                    assertThat(conflict.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    return false;
                }
            };
            var one = executor.submit(create);
            var two = executor.submit(create);
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Fixture fixture() {
        UUID carrier = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID calendar = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO carriers (id,code,name,provider_type,status,created_at,updated_at)
                VALUES (?,?,'Test carrier','MOCK','ACTIVE',NOW(),NOW())
                """, carrier.toString(), carrier.toString());
        jdbc.update("""
                INSERT INTO warehouses (id,code,name,status,created_at,updated_at)
                VALUES (?,?,'Test warehouse','ACTIVE',NOW(),NOW())
                """, warehouse.toString(), warehouse.toString());
        jdbc.update("""
                INSERT INTO shipping_calendars (id,name,purpose,workday_mask,opens_at,closes_at,cutoff_at,holidays,active)
                VALUES (?,'Test calendar','CARRIER_TRANSPORT',127,'08:00:00','17:00:00','15:00:00','[]',TRUE)
                """, calendar.toString());
        jdbc.update("""
                INSERT INTO store_carrier_configs (id,carrier_id,enabled,default_service_code,default_pickup_warehouse_id,created_at,updated_at)
                VALUES (?,?,TRUE,'TEST_SERVICE',?,NOW(),NOW())
                """, UUID.randomUUID().toString(), carrier.toString(), warehouse.toString());
        var access = mock(DeliveryEstimateAccess.class);
        when(access.requireManager()).thenReturn(UUID.randomUUID());
        return new Fixture(carrier, warehouse, calendar,
                new DeliveryEstimateRuleService(client, new ObjectMapper().findAndRegisterModules(), new AddressCatalog(client), access));
    }

    private record Fixture(UUID carrier, UUID warehouse, UUID calendar, DeliveryEstimateRuleService service) {
        Route route(String wardCode) {
            return new Route(carrier, "TEST_SERVICE", warehouse, "01", wardCode);
        }

        Terms terms(int min, int max, int priority) {
            return new Terms(calendar, min, max, priority, START, END, true, false, "test-coverage");
        }
    }
}
