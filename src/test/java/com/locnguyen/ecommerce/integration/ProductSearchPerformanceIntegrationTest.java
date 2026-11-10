package com.locnguyen.ecommerce.integration;

import com.locnguyen.ecommerce.domains.product.dto.ProductFilter;
import com.locnguyen.ecommerce.domains.product.service.ProductService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in, repeatable performance gate for the deployed MariaDB FULLTEXT product-search path.
 *
 * <p>Run explicitly; it is disabled in normal unit/integration suites because it seeds a
 * realistic catalog and deliberately applies peak-like concurrency:
 *
 * <pre>
 * mvn -Dsearch.performance.enabled=true -Dtest=ProductSearchPerformanceIntegrationTest test
 * </pre>
 *
 * <p>The gate represents a 100,000-product catalog and 100 concurrent catalog searches. It
 * measures the service path (FULLTEXT query, entity hydration and list-item mapping), excluding
 * network transit. It therefore gives a conservative application-and-database latency signal;
 * browser, gateway and network latency must be monitored separately in a staging deployment.
 */
@Tag("performance")
@EnabledIfSystemProperty(named = "search.performance.enabled", matches = "true")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProductSearchPerformanceIntegrationTest {

    private static final int CATALOG_SIZE = Integer.getInteger("search.performance.catalog-size", 100_000);
    private static final int CONCURRENT_USERS = Integer.getInteger("search.performance.concurrent-users", 100);
    private static final int WARMUP_REQUESTS = Integer.getInteger("search.performance.warmup-requests", 100);
    private static final int REQUESTS_PER_USER = Integer.getInteger("search.performance.requests-per-user", 10);
    private static final long P95_TARGET_MILLIS = Long.getLong("search.performance.p95-target-ms", 300L);
    private static final long P99_TARGET_MILLIS = Long.getLong("search.performance.p99-target-ms", 600L);

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("ecommerce_search_performance")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> Math.min(CONCURRENT_USERS, 32));
        registry.add("spring.jpa.open-in-view", () -> false);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProductService productService;

    @Test
    void fulltextSearch_atPeakLikeConcurrency_meetsCatalogLatencyBudget() throws Exception {
        seedCatalog();
        printCommonKeywordExplainPlan();

        List<SearchScenario> scenarios = List.of(
                new SearchScenario("common-keyword", "ao thun", null),
                new SearchScenario("filtered-keyword", "ao thun", true),
                new SearchScenario("rare-keyword", "limited signature", null),
                new SearchScenario("no-result", "unmatched search phrase", null)
        );

        List<String> violations = new ArrayList<>();
        for (SearchScenario scenario : scenarios) {
            LatencySummary summary = measure(scenario);
            System.out.printf(Locale.ROOT,
                    "SEARCH_PERF scenario=%s catalog=%d concurrentUsers=%d requests=%d "
                            + "p50Ms=%d p95Ms=%d p99Ms=%d maxMs=%d errors=%d errorTypes=%s%n",
                    scenario.name(), CATALOG_SIZE, CONCURRENT_USERS, summary.samples().size(),
                    summary.percentile(50), summary.percentile(95), summary.percentile(99),
                    summary.maxMillis(), summary.errors(), summary.errorTypes());

            if (summary.errors() > 0) {
                violations.add(scenario.name() + " errors=" + summary.errors());
            }
            if (summary.percentile(95) > P95_TARGET_MILLIS) {
                violations.add(scenario.name() + " p95=" + summary.percentile(95)
                        + "ms (budget=" + P95_TARGET_MILLIS + "ms)");
            }
            if (summary.percentile(99) > P99_TARGET_MILLIS) {
                violations.add(scenario.name() + " p99=" + summary.percentile(99)
                        + "ms (budget=" + P99_TARGET_MILLIS + "ms)");
            }
        }
        assertThat(violations).as("Peak-like catalog search latency budget violations").isEmpty();
    }

    private void seedCatalog() {
        jdbcTemplate.execute("SET foreign_key_checks = 0");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.execute("SET foreign_key_checks = 1");

        Instant now = Instant.now();
        int batchSize = 1_000;
        for (int batchStart = 0; batchStart < CATALOG_SIZE; batchStart += batchSize) {
            int start = batchStart;
            int count = Math.min(batchSize, CATALOG_SIZE - batchStart);
            jdbcTemplate.batchUpdate("""
                    INSERT INTO products (
                        id, name, slug, short_description, description, search_text, status,
                        is_featured, created_at, updated_at, is_deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, FALSE)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int offset) throws SQLException {
                    int productNumber = start + offset;
                    SearchDocument document = searchDocument(productNumber);
                    statement.setString(1, "00000000-0000-0000-0000-" + String.format(Locale.ROOT, "%012d", productNumber + 1));
                    statement.setString(2, document.name());
                    statement.setString(3, "search-performance-" + productNumber);
                    statement.setString(4, "Synthetic catalog item for search performance measurement");
                    statement.setString(5, "Synthetic product document used only by the isolated performance benchmark.");
                    statement.setString(6, document.searchText());
                    statement.setBoolean(7, productNumber % 2 == 0);
                    statement.setTimestamp(8, Timestamp.from(now));
                    statement.setTimestamp(9, Timestamp.from(now));
                }

                @Override
                public int getBatchSize() {
                    return count;
                }
            });
        }

        for (int batchStart = 0; batchStart < CATALOG_SIZE; batchStart += batchSize) {
            int start = batchStart;
            int count = Math.min(batchSize, CATALOG_SIZE - batchStart);
            jdbcTemplate.batchUpdate("""
                    INSERT INTO product_variants (
                        id, product_id, sku, variant_name, base_price, status,
                        created_at, updated_at, is_deleted
                    ) VALUES (?, ?, ?, 'Default', 199000.00, 'ACTIVE', ?, ?, FALSE)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int offset) throws SQLException {
                    int productNumber = start + offset;
                    statement.setString(1, variantId(productNumber));
                    statement.setString(2, productId(productNumber));
                    statement.setString(3, "SEARCH-PERF-" + productNumber);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setTimestamp(5, Timestamp.from(now));
                }

                @Override
                public int getBatchSize() {
                    return count;
                }
            });
            jdbcTemplate.batchUpdate("""
                    INSERT INTO product_media (
                        id, product_id, media_url, media_type, sort_order, is_primary, created_at, updated_at
                    ) VALUES (?, ?, ?, 'IMAGE', 0, TRUE, ?, ?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int offset) throws SQLException {
                    int productNumber = start + offset;
                    statement.setString(1, mediaId(productNumber));
                    statement.setString(2, productId(productNumber));
                    statement.setString(3, "https://example.test/search-performance/" + productNumber + ".jpg");
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setTimestamp(5, Timestamp.from(now));
                }

                @Override
                public int getBatchSize() {
                    return count;
                }
            });
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class))
                .isEqualTo(CATALOG_SIZE);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_variants", Integer.class))
                .isEqualTo(CATALOG_SIZE);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_media", Integer.class))
                .isEqualTo(CATALOG_SIZE);
    }

    private LatencySummary measure(SearchScenario scenario) throws Exception {
        for (int request = 0; request < WARMUP_REQUESTS; request++) {
            search(scenario);
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        try {
            List<Callable<List<Measurement>>> tasks = new ArrayList<>();
            for (int user = 0; user < CONCURRENT_USERS; user++) {
                tasks.add(() -> measureUserRequests(scenario));
            }

            List<Future<List<Measurement>>> futures = executor.invokeAll(tasks);
            List<Long> samples = new ArrayList<>(CONCURRENT_USERS * REQUESTS_PER_USER);
            int errors = 0;
            TreeSet<String> errorTypes = new TreeSet<>();
            for (Future<List<Measurement>> future : futures) {
                for (Measurement measurement : future.get()) {
                    samples.add(measurement.elapsedMillis());
                    if (measurement.failed()) {
                        errors++;
                        errorTypes.add(measurement.failureType());
                    }
                }
            }
            return new LatencySummary(samples, errors, List.copyOf(errorTypes));
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Measurement> measureUserRequests(SearchScenario scenario) {
        List<Measurement> measurements = new ArrayList<>(REQUESTS_PER_USER);
        for (int request = 0; request < REQUESTS_PER_USER; request++) {
            long startedAt = System.nanoTime();
            String failureType = null;
            try {
                search(scenario);
            } catch (RuntimeException exception) {
                failureType = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            measurements.add(new Measurement(elapsedMillis, failureType));
        }
        return measurements;
    }

    private void search(SearchScenario scenario) {
        ProductFilter filter = ProductFilter.builder()
                .keyword(scenario.keyword())
                .featured(scenario.featured())
                .build();
        productService.getPublishedProducts(
                filter,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    private static SearchDocument searchDocument(int productNumber) {
        if (productNumber % 100 == 0) {
            return new SearchDocument("Limited Signature " + productNumber,
                    "limited signature collection " + productNumber);
        }
        if (productNumber % 5 == 0) {
            return new SearchDocument("Ao Thun Everyday " + productNumber,
                    "ao thun everyday vietnam fashion " + productNumber);
        }
        return new SearchDocument("Catalog Product " + productNumber,
                "catalog product fashion " + productNumber);
    }

    private void printCommonKeywordExplainPlan() {
        List<java.util.Map<String, Object>> plan = jdbcTemplate.queryForList("""
                EXPLAIN SELECT p.id FROM products p
                WHERE MATCH(p.name, p.slug, p.search_text) AGAINST (? IN BOOLEAN MODE)
                  AND p.is_deleted = FALSE
                  AND p.status = 'PUBLISHED'
                ORDER BY MATCH(p.name, p.slug, p.search_text) AGAINST (? IN BOOLEAN MODE) DESC
                LIMIT 20
                """, "+ao* +thun*", "+ao* +thun*");
        System.out.println("SEARCH_PERF_EXPLAIN " + plan);
    }

    private static String productId(int productNumber) {
        return "00000000-0000-0000-0000-" + String.format(Locale.ROOT, "%012d", productNumber + 1);
    }

    private static String variantId(int productNumber) {
        return "10000000-0000-0000-0000-" + String.format(Locale.ROOT, "%012d", productNumber + 1);
    }

    private static String mediaId(int productNumber) {
        return "20000000-0000-0000-0000-" + String.format(Locale.ROOT, "%012d", productNumber + 1);
    }

    private record SearchScenario(String name, String keyword, Boolean featured) {}

    private record SearchDocument(String name, String searchText) {}

    private record Measurement(long elapsedMillis, String failureType) {
        boolean failed() {
            return failureType != null;
        }
    }

    private record LatencySummary(List<Long> samples, int errors, List<String> errorTypes) {
        private LatencySummary {
            samples = new ArrayList<>(samples);
            Collections.sort(samples);
        }

        long percentile(int percentile) {
            int index = (int) Math.ceil(percentile / 100.0 * samples.size()) - 1;
            return samples.get(Math.max(0, index));
        }

        long maxMillis() {
            return samples.get(samples.size() - 1);
        }
    }
}
