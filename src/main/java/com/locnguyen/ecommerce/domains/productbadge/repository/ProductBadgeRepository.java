package com.locnguyen.ecommerce.domains.productbadge.repository;

import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProductBadgeRepository {
    private final JdbcClient jdbc;

    public List<Badge> list(int offset, int limit) {
        return jdbc.sql("""
                SELECT id, code, label, active, priority, version FROM product_badges
                ORDER BY priority, code LIMIT :limit OFFSET :offset
                """).param("limit", limit).param("offset", offset).query(this::badge).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM product_badges").query(Long.class).single();
    }

    public Optional<Badge> find(UUID id) {
        return jdbc.sql("SELECT id, code, label, active, priority, version FROM product_badges WHERE id = :id")
                .param("id", id.toString()).query(this::badge).optional();
    }

    public void insert(UUID id, Input input) {
        jdbc.sql("INSERT INTO product_badges (id, code, label, active, priority) VALUES (:id, :code, :label, :active, :priority)")
                .param("id", id.toString()).param("code", input.code()).param("label", input.label().trim())
                .param("active", input.active()).param("priority", input.priority()).update();
    }

    public int update(UUID id, Input input) {
        return jdbc.sql("""
                UPDATE product_badges SET code = :code, label = :label, active = :active, priority = :priority,
                    version = version + 1
                WHERE id = :id AND version = :version
                """).param("id", id.toString()).param("version", input.version()).param("code", input.code())
                .param("label", input.label().trim()).param("active", input.active()).param("priority", input.priority()).update();
    }

    public List<ProductReference> assignedProducts(UUID id, int offset, int limit) {
        return jdbc.sql("""
                SELECT p.id, p.name FROM product_badge_links a JOIN products p ON p.id = a.product_id
                WHERE a.badge_id = :id AND p.is_deleted = FALSE ORDER BY p.name, p.id LIMIT :limit OFFSET :offset
                """).param("id", id.toString()).param("limit", limit).param("offset", offset)
                .query((rs, row) -> new ProductReference(UUID.fromString(rs.getString("id")), rs.getString("name"))).list();
    }

    public long countAssigned(UUID id) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM product_badge_links a JOIN products p ON p.id = a.product_id
                WHERE a.badge_id = :id AND p.is_deleted = FALSE
                """).param("id", id.toString()).query(Long.class).single();
    }

    public boolean productExists(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM products WHERE id = :id AND is_deleted = FALSE)")
                .param("id", id.toString()).query(Boolean.class).single();
    }

    public void link(UUID badgeId, UUID productId) {
        jdbc.sql("""
                INSERT INTO product_badge_links (product_id, badge_id) VALUES (:product, :badge)
                ON DUPLICATE KEY UPDATE badge_id = VALUES(badge_id)
                """).param("product", productId.toString()).param("badge", badgeId.toString()).update();
    }

    public void unlink(UUID badgeId, UUID productId) {
        jdbc.sql("DELETE FROM product_badge_links WHERE product_id = :product AND badge_id = :badge")
                .param("product", productId.toString()).param("badge", badgeId.toString()).update();
    }

    public Map<UUID, List<Display>> findActive(List<UUID> products, int limit) {
        if (products.isEmpty()) return Map.of();

        record Match(UUID productId, Display badge) {}
        List<Match> matches = jdbc.sql("""
                SELECT product_id, code, label FROM (
                    SELECT a.product_id, b.code, b.label,
                           ROW_NUMBER() OVER (PARTITION BY a.product_id ORDER BY b.priority, b.code) AS rank_no
                    FROM product_badge_links a
                    JOIN product_badges b ON b.id = a.badge_id AND b.active = TRUE
                    JOIN products p ON p.id = a.product_id AND p.is_deleted = FALSE AND p.status = 'PUBLISHED'
                    WHERE a.product_id IN (:products)
                ) ranked WHERE rank_no <= :limit ORDER BY product_id, rank_no
                """).param("products", products.stream().map(UUID::toString).toList()).param("limit", limit)
                .query((rs, row) -> new Match(UUID.fromString(rs.getString("product_id")),
                        new Display(rs.getString("code"), rs.getString("label")))).list();

        Map<UUID, List<Display>> result = new HashMap<>();
        for (Match match : matches) {
            result.computeIfAbsent(match.productId(), id -> new ArrayList<>()).add(match.badge());
        }
        return result;
    }

    private Badge badge(ResultSet rs, int row) throws SQLException {
        return new Badge(UUID.fromString(rs.getString("id")), rs.getString("code"), rs.getString("label"),
                rs.getBoolean("active"), rs.getInt("priority"), rs.getLong("version"));
    }
}
