package com.locnguyen.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels;
import com.locnguyen.ecommerce.domains.productbadge.repository.ProductBadgeRepository;
import com.locnguyen.ecommerce.domains.productbadge.service.impl.ProductBadgeServiceImpl;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.domains.sizeguide.dto.SizeGuideModels.*;
import com.locnguyen.ecommerce.domains.sizeguide.repository.SizeGuideRepository;
import com.locnguyen.ecommerce.domains.sizeguide.service.impl.SizeGuideServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class CatalogPresentationIntegrationTest {
    @Container
    private static final MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:11.4");
    private static JdbcTemplate jdbc;
    private static SizeGuideServiceImpl service;
    private static ProductBadgeServiceImpl badges;

    @BeforeAll
    static void prepareDatabase() {
        var dataSource = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        service = new SizeGuideServiceImpl(new SizeGuideRepository(JdbcClient.create(dataSource), new ObjectMapper()));
        badges = new ProductBadgeServiceImpl(new ProductBadgeRepository(JdbcClient.create(dataSource)));
    }

    @Test
    void resolvesAllScopesWithCategoryPriorityAndFallsBackWhenInactive() {
        UUID brand = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        jdbc.update("INSERT INTO brands (id, name, slug, created_at, updated_at) VALUES (?, 'Brand', ?, NOW(), NOW())", brand.toString(), brand.toString());
        addCategory(first, 10);
        addCategory(second, 20);
        jdbc.update("INSERT INTO products (id, name, slug, brand_id, status, created_at, updated_at) VALUES (?, 'Demo', ?, ?, 'PUBLISHED', NOW(), NOW())",
                product.toString(), product.toString(), brand.toString());
        jdbc.update("INSERT INTO product_categories VALUES (?, ?), (?, ?)",
                product.toString(), first.toString(), product.toString(), second.toString());

        Guide shop = create("Shop");
        Guide category = create("Category");
        Guide alternateCategory = create("Other category");
        Guide brandGuide = create("Brand");
        Guide categoryBrand = create("Category plus brand");
        Guide direct = create("Direct");
        assertThat(service.resolve(product)).isNull();
        service.assign(shop.id(), new AssignmentInput(Scope.SHOP, null, null, null, 100));
        assertResolved(product, shop);
        service.assign(category.id(), new AssignmentInput(Scope.CATEGORY, null, first, null, 100));
        service.assign(alternateCategory.id(), new AssignmentInput(Scope.CATEGORY, null, second, null, 10));
        assertResolved(product, alternateCategory);
        service.assign(alternateCategory.id(), new AssignmentInput(Scope.CATEGORY, null, second, null, 100));
        assertResolved(product, category);
        service.assign(brandGuide.id(), new AssignmentInput(Scope.BRAND, null, null, brand, 100));
        assertResolved(product, brandGuide);
        service.assign(categoryBrand.id(), new AssignmentInput(Scope.CATEGORY_BRAND, null, first, brand, 100));
        assertResolved(product, categoryBrand);
        service.assign(direct.id(), new AssignmentInput(Scope.PRODUCT, product, null, null, 100));
        assertResolved(product, direct);
        assertThat(service.assignments(direct.id())).extracting(Assignment::productName).containsExactly("Demo");

        var replacement = create("Replacement");
        service.assign(replacement.id(), new AssignmentInput(Scope.PRODUCT, product, null, null, 100));
        assertThat(service.assignments(direct.id())).isEmpty();
        assertResolved(product, replacement);
        service.update(replacement.id(), new Input(replacement.name(), false, replacement.version(), replacement.content()));
        assertResolved(product, categoryBrand);
        assertThatThrownBy(() -> service.update(replacement.id(),
                new Input(replacement.name(), true, replacement.version(), replacement.content())))
                .isInstanceOf(AppException.class).hasMessageContaining("changed");
        var copy = service.copy(categoryBrand.id());
        assertThat(copy.active()).isFalse();
        assertThat(copy.content()).isEqualTo(categoryBrand.content());
        assertThat(service.assignments(copy.id())).isEmpty();
        jdbc.update("UPDATE products SET status = 'DRAFT' WHERE id = ?", product.toString());
        assertThat(service.resolve(product)).isNull();
    }

    @Test
    void rejectsIncompleteRowsAndReversedRanges() {
        var column = new Column("chest", "Vòng ngực", Unit.CM);
        var empty = new Content(Measurement.BODY, List.of(column), List.of(new Row("S", Map.of())), null, null);
        var reversed = new Content(Measurement.BODY, List.of(column),
                List.of(new Row("S", Map.of("chest", new Range(BigDecimal.TEN, BigDecimal.ONE)))), null, null);
        assertThatThrownBy(() -> service.create(new Input("Invalid", false, null, empty))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.create(new Input("Invalid", false, null, reversed))).isInstanceOf(AppException.class);
    }

    @Test
    void ranksActiveBadgesInOneBatchAndDoesNotDuplicateAssignments() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, status, created_at, updated_at) VALUES (?, 'Demo', ?, 'PUBLISHED', NOW(), NOW()), (?, 'Second', ?, 'PUBLISHED', NOW(), NOW())",
                first.toString(), first.toString(), second.toString(), second.toString());

        var top = badges.create(new ProductBadgeModels.Input("TOP_DEMO", "Top badge", true, 3, null));
        var middle = badges.create(new ProductBadgeModels.Input("MID_DEMO", "Middle badge", true, 10, null));
        var last = badges.create(new ProductBadgeModels.Input("LAST_DEMO", "Last badge", true, 10, null));
        badges.assign(top.id(), first);
        badges.assign(top.id(), first);
        badges.assign(middle.id(), first);
        badges.assign(last.id(), first);
        badges.assign(middle.id(), second);

        assertThat(badges.products(top.id(), PageRequest.of(0, 20)).getTotalItems()).isEqualTo(1);
        var summaries = badges.findActive(List.of(first, second), 1);
        assertThat(summaries.get(first)).extracting(ProductBadgeModels.Display::code).containsExactly("TOP_DEMO");
        assertThat(summaries.get(second)).extracting(ProductBadgeModels.Display::code).containsExactly("MID_DEMO");
        assertThat(badges.findActive(List.of(first), 2).get(first)).extracting(ProductBadgeModels.Display::code)
                .containsExactly("TOP_DEMO", "LAST_DEMO");

        badges.update(top.id(), new ProductBadgeModels.Input(top.code(), top.label(), false, 3, top.version()));
        assertThat(badges.findActive(List.of(first), 2).get(first)).extracting(ProductBadgeModels.Display::code)
                .containsExactly("LAST_DEMO", "MID_DEMO");
        assertThatThrownBy(() -> badges.create(new ProductBadgeModels.Input(middle.code(), "Duplicate", true, 2, null)))
                .isInstanceOf(AppException.class);
        badges.unassign(middle.id(), second);
        assertThat(badges.findActive(List.of(second), 1)).isEmpty();
    }

    private static Guide create(String name) {
        var column = new Column("chest", "Vòng ngực", Unit.CM);
        var content = new Content(Measurement.BODY, List.of(column), List.of(
                new Row("S", Map.of("chest", new Range(BigDecimal.valueOf(84), BigDecimal.valueOf(88)))),
                new Row("M", Map.of("chest", new Range(BigDecimal.valueOf(90), null)))), null, null);
        return service.create(new Input(name, true, null, content));
    }

    private static void assertResolved(UUID product, Guide guide) {
        assertThat(service.resolve(product)).isEqualTo(new Display(guide.id(), guide.name(), guide.content()));
    }

    private static void addCategory(UUID id, int order) {
        jdbc.update("INSERT INTO categories (id, name, slug, sort_order, created_at, updated_at) VALUES (?, 'Category', ?, ?, NOW(), NOW())",
                id.toString(), id.toString(), order);
    }
}
