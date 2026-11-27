package com.locnguyen.ecommerce.domains.order.service.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.domains.invoice.tax.TaxInvoiceDocuments;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.service.tax.TaxInputs.*;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class VatConfigurationIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4");
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;

    @BeforeAll
    static void migrate() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @AfterEach
    void cleanupSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void capturesConfirmedTotalsAndKeepsSnapshotAfterNewRateRevision() {
        UUID adminId = UUID.randomUUID();
        UserRepository users = authenticate(adminId);
        var settings = new TaxSettings(true, true, "STANDARD_REDUCIBLE", "SHIPPING_STANDARD", Set.of(adminId));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var config = new TaxConfigurationService(JdbcClient.create(jdbc), settings, users, mapper);
        var snapshot = new OrderTaxEstimates(settings, JdbcClient.create(jdbc), mapper);
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        RateInput eight = new RateInput(new BigDecimal("0.08"), date.minusDays(2), date, true);
        RateInput ten = new RateInput(new BigDecimal("0.10"), date.minusDays(3), null, true);
        var nextRate = new RateInput(new BigDecimal("0.10"), date, null, true);
        tx.execute(status -> config.configure("STANDARD_REDUCIBLE", new ScheduleUpdate(0, List.of(eight, nextRate))));
        tx.execute(status -> config.create(new CategoryInput("SHIPPING_STANDARD", "Shipping for test",
                InclusiveVatCalculator.Treatment.TAXABLE)));
        tx.execute(status -> config.configure("SHIPPING_STANDARD", new ScheduleUpdate(0,
                List.of(new RateInput(new BigDecimal("0.08"), date.minusDays(3), null, true)))));
        tx.execute(status -> config.configure("STANDARD_NON_REDUCIBLE", new ScheduleUpdate(0, List.of(ten))));
        assertThatThrownBy(() -> tx.execute(status -> config.configure("STANDARD_REDUCIBLE",
                new ScheduleUpdate(1, List.of(eight, ten)))))
                .isInstanceOf(AppException.class).hasMessageContaining("Overlapping");
        assertThatThrownBy(() -> tx.execute(status -> config.configure("STANDARD_REDUCIBLE",
                new ScheduleUpdate(0, List.of(ten)))))
                .isInstanceOf(AppException.class).hasMessageContaining("schedule changed");

        String orderId = seedOrder();
        Product a = product();
        Product b = product();
        tx.execute(status -> config.assign(b.getId(), new Assignment("STANDARD_NON_REDUCIBLE")));
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", UUID.fromString(orderId));
        order.getItems().add(item(a, 1_080_000, 108_000));
        order.getItems().add(item(b, 550_000, 55_000));
        order.setShippingFee(BigDecimal.valueOf(21_600));
        order.setTotalAmount(BigDecimal.valueOf(1_488_600));
        var estimatedAt = date.minusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        tx.executeWithoutResult(status -> snapshot.capture(order, estimatedAt));
        var first = snapshot.get(order.getId());
        assertThat(first.vat()).isEqualByComparingTo("118600");
        assertThat(first.gross()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(first.lines()).hasSize(3);
        var evidence = new OrderTaxEstimates.TransferEvidence(UUID.randomUUID(), Instant.now(), "test-transfer");
        var finalized = tx.execute(status -> snapshot.captureFinal(order.getId(), evidence));
        assertThat(finalized.amounts().vat()).isEqualByComparingTo("134964");
        assertThat(finalized.amounts().gross()).isEqualByComparingTo(first.gross());
        tx.execute(status -> config.configure("STANDARD_REDUCIBLE", new ScheduleUpdate(1, List.of(ten))));
        assertThat(snapshot.get(order.getId())).isEqualTo(first);
        assertThat(snapshot.getFinal(order.getId())).isEqualTo(finalized);
        var replay = tx.execute(status -> snapshot.captureFinal(order.getId(), evidence));
        assertThat(replay).isEqualTo(finalized);
        assertThatThrownBy(() -> tx.execute(status -> snapshot.captureFinal(order.getId(),
                new OrderTaxEstimates.TransferEvidence(UUID.randomUUID(), evidence.transferredAt(), "other-transfer"))))
                .isInstanceOf(AppException.class).hasMessageContaining("different evidence");
        assertThat(config.audit("STANDARD_REDUCIBLE", 0)).hasSize(2);
        assertThat(config.get("STANDARD_REDUCIBLE").category().revision()).isEqualTo(2);

        var documents = new TaxInvoiceDocuments(JdbcClient.create(jdbc), snapshot,
                new TaxInvoiceDocuments.Settings(true, Set.of(), Set.of(adminId)), users);
        UUID invoiceId = seedInvoice(order.getId());
        assertThat(documents.preparation(invoiceId).snapshot()).isEqualTo(finalized);
        assertThat(documents.exportCsv(invoiceId)).contains("invoice_code,order_id")
                .contains("\"21600\",\"20000\",\"1600\"");
        var invoiceEvidence = new TaxInvoiceDocuments.ExternalEvidence(
                "test-issuer", "TEST", "00001", "external-test-1", evidence.transferredAt());
        var external = tx.execute(status -> documents.record(invoiceId, invoiceEvidence));
        assertThat(external.recordedBy()).isEqualTo(adminId);
        var retry = tx.execute(status -> documents.record(invoiceId, invoiceEvidence));
        assertThat(retry).isEqualTo(external);
        assertThatThrownBy(() -> tx.execute(status -> documents.record(invoiceId,
                new TaxInvoiceDocuments.ExternalEvidence("test-issuer", "TEST", "00002",
                        "external-test-2", evidence.transferredAt()))))
                .isInstanceOf(AppException.class).hasMessageContaining("already recorded");

        var disabled = new TaxInvoiceDocuments(JdbcClient.create(jdbc), snapshot,
                new TaxInvoiceDocuments.Settings(false, Set.of(adminId), Set.of(adminId)), users);
        assertThat(disabled.preparation(invoiceId).canRecord()).isFalse();
        assertThatThrownBy(() -> disabled.record(invoiceId, invoiceEvidence))
                .isInstanceOf(AppException.class);
    }

    private static UserRepository authenticate(UUID id) {
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.ADMIN);
        when(user.getId()).thenReturn(id);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRoles()).thenReturn(Set.of(role));
        when(users.findByEmailAndDeletedFalse("vat@example.test")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "vat@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        return users;
    }

    private static Product product() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id,name,slug,status,created_at,updated_at) VALUES (?, 'VAT fixture', ?, 'PUBLISHED', NOW(), NOW())", id.toString(), id.toString());
        var product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private static OrderItem item(Product product, long gross, long discount) {
        var variant = new ProductVariant();
        variant.setProduct(product);
        var item = new OrderItem();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setVariant(variant);
        item.setLineTotal(BigDecimal.valueOf(gross));
        item.setVoucherDiscountAmount(BigDecimal.valueOf(discount));
        item.setNetLineAmount(BigDecimal.valueOf(gross - discount));
        return item;
    }

    private static String seedOrder() {
        String user = UUID.randomUUID().toString();
        String customer = UUID.randomUUID().toString();
        String order = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id,email,password_hash,created_at,updated_at) VALUES (?, ?, 'UNUSABLE', NOW(), NOW())", user, user + "@example.test");
        jdbc.update("INSERT INTO customers (id,user_id,created_at,updated_at) VALUES (?, ?, NOW(), NOW())", customer, user);
        jdbc.update("""
                INSERT INTO orders (id,customer_id,order_code,receiver_name,receiver_phone,shipping_street,shipping_ward,
                    shipping_district,shipping_city,sub_total,discount_amount,shipping_fee,total_amount,created_at,updated_at)
                VALUES (?,?,?,'Fixture','0000000000','Test','Test','Test','Test',1630000,163000,21600,1488600,NOW(),NOW())
                """, order, customer, order);
        return order;
    }

    private static UUID seedInvoice(UUID orderId) {
        UUID invoice = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO invoices (id,order_id,invoice_code,customer_name,customer_email,billing_street,
                    billing_ward,billing_district,billing_city,sub_total,total_amount,issued_at,created_at,updated_at)
                VALUES (?,?,?,'Fixture','vat@example.test','Test','Test','Test','Test',1630000,1488600,NOW(),NOW(),NOW())
                """, invoice.toString(), orderId.toString(), invoice.toString());
        return invoice;
    }
}
