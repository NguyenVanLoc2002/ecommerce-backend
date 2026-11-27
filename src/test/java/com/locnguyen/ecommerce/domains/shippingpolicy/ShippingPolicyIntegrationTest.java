package com.locnguyen.ecommerce.domains.shippingpolicy;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.domains.shippingpolicy.ShippingPolicyModels.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
class ShippingPolicyIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4");
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private final UUID editor = UUID.randomUUID();
    private final UUID publisher = UUID.randomUUID();
    private final ShippingPolicyAccess access = mock(ShippingPolicyAccess.class);
    private ShippingPolicyService service;

    @BeforeAll
    static void migrate() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM shipping_policy_history");
        jdbc.update("DELETE FROM shipping_policy_versions");
        when(access.requireEdit()).thenReturn(editor);
        when(access.requirePublish()).thenReturn(publisher);
        when(access.requireRead()).thenReturn(editor);
        when(access.publicationEnabled()).thenReturn(true);
        service = new ShippingPolicyService(JdbcClient.create(jdbc), access);
    }

    @Test
    void publishesOnlyEffectiveVersionAndKeepsImmutableRevisionHistory() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Draft first = new Draft("Chính sách giao hàng", "Giao hàng theo vùng phục vụ.", now.minusSeconds(60));
        Policy draft = transaction.execute(tx -> service.create(first));
        assertThatThrownBy(service::published).isInstanceOf(AppException.class);
        Policy edited = transaction.execute(tx -> service.edit(draft.version(), new Edit(draft.revision(),
                new Draft(first.title(), "Nội dung đã duyệt.", first.effectiveFrom()))));
        assertThatThrownBy(() -> transaction.execute(tx -> service.edit(draft.version(),
                new Edit(draft.revision(), first)))).isInstanceOf(AppException.class).hasMessageContaining("revision");
        Policy published = transaction.execute(tx -> service.publish(edited.version(), edited.revision()));
        assertThat(service.published().content()).isEqualTo("Nội dung đã duyệt.");
        assertThat(published.publishedBy()).isEqualTo(publisher);
        assertThat(service.history(draft.version(), 0).items())
                .extracting(Change::actor).containsExactly(publisher, editor, editor);
        assertThatThrownBy(() -> transaction.execute(tx -> service.edit(published.version(),
                new Edit(published.revision(), first)))).isInstanceOf(AppException.class);

        Policy upcoming = transaction.execute(tx -> service.create(new Draft("Scheduled", "Future", now.plusSeconds(3600))));
        Policy scheduled = transaction.execute(tx -> service.publish(upcoming.version(), upcoming.revision()));
        assertThat(service.published().version()).isEqualTo(published.version());
        transaction.execute(tx -> service.archive(scheduled.version(), scheduled.revision()));
        assertThat(service.published().version()).isEqualTo(published.version());

        Policy next = transaction.execute(tx -> service.create(new Draft("Replacement", "Current", now)));
        Policy replacement = transaction.execute(tx -> service.publish(next.version(), next.revision()));
        assertThat(service.published().version()).isEqualTo(next.version());
        transaction.execute(tx -> service.archive(replacement.version(), replacement.revision()));
        assertThatThrownBy(service::published).isInstanceOf(AppException.class);
        assertThat(service.list(0).items()).hasSize(3);
    }

    @Test
    void rejectsPublicationWhenOperationsGateIsDisabled() {
        when(access.publicationEnabled()).thenReturn(false);
        var draft = transaction.execute(tx -> service.create(new Draft("Title", "Content", Instant.now())));
        assertThatThrownBy(() -> transaction.execute(tx -> service.publish(draft.version(), draft.revision())))
                .isInstanceOf(AppException.class).hasMessageContaining("disabled");
        assertThatThrownBy(service::published).isInstanceOf(AppException.class);
        assertThat(service.history(draft.version(), 0).items()).hasSize(1);
    }

    @Test
    void validatesProvinceWardMembershipAndRejectsInactiveCodes() {
        jdbc.update("INSERT INTO address_provinces (code,name) VALUES ('01','First'),('79','Second')");
        jdbc.update("INSERT INTO address_wards (code,province_code,name) VALUES ('00004','01','Ward 1'),('26734','79','Ward 2')");
        var catalog = new com.locnguyen.ecommerce.domains.addresscatalog.AddressCatalog(JdbcClient.create(jdbc));
        assertThat(catalog.provinces()).hasSize(2);
        assertThat(catalog.wards("01")).containsExactly(
                new com.locnguyen.ecommerce.domains.addresscatalog.AddressCatalog.Ward("00004", "Ward 1", "01"));
        assertThat(catalog.resolve("01", "00004").countryCode()).isEqualTo("VN");
        assertThatThrownBy(() -> catalog.resolve("01", "26734")).isInstanceOf(AppException.class);
        jdbc.update("UPDATE address_wards SET active=FALSE WHERE code='00004'");
        assertThat(catalog.wards("01")).isEmpty();
        assertThatThrownBy(() -> catalog.resolve("01", "00004")).isInstanceOf(AppException.class);
    }
}
