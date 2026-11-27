package com.locnguyen.ecommerce.domains.sizeguide.repository;

import com.locnguyen.ecommerce.domains.sizeguide.dto.SizeGuideModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SizeGuideRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public List<Summary> summaries(String name, int offset, int limit) {
        return jdbc.sql("""
                SELECT id, name, active, version FROM size_guides
                WHERE name LIKE :name ESCAPE '!'
                ORDER BY name, id LIMIT :limit OFFSET :offset
                """).param("name", name).param("limit", limit).param("offset", offset)
                .query((rs, row) -> new Summary(uuid(rs, "id"), rs.getString("name"),
                        rs.getBoolean("active"), rs.getLong("version"))).list();
    }

    public long count(String name) {
        return jdbc.sql("SELECT COUNT(*) FROM size_guides WHERE name LIKE :name ESCAPE '!'")
                .param("name", name).query(Long.class).single();
    }

    public Optional<Guide> find(UUID id) {
        return jdbc.sql("SELECT id, name, active, version, content FROM size_guides WHERE id = :id")
                .param("id", id.toString()).query((rs, row) -> new Guide(uuid(rs, "id"), rs.getString("name"),
                        rs.getBoolean("active"), rs.getLong("version"), read(rs.getString("content")))).optional();
    }

    public void insert(UUID id, Input input) {
        jdbc.sql("INSERT INTO size_guides (id, name, active, content) VALUES (:id, :name, :active, :content)")
                .param("id", id.toString()).param("name", input.name().trim()).param("active", input.active())
                .param("content", write(input.content())).update();
    }

    public int update(UUID id, Input input) {
        return jdbc.sql("""
                UPDATE size_guides SET name = :name, active = :active, content = :content, version = version + 1
                WHERE id = :id AND version = :version
                """).param("id", id.toString()).param("name", input.name().trim()).param("active", input.active())
                .param("version", input.version()).param("content", write(input.content())).update();
    }

    public List<Assignment> assignments(UUID id) {
        return jdbc.sql("""
                SELECT a.scope_key, a.guide_id, a.scope, a.product_id, a.category_id, a.brand_id, a.priority,
                       p.name AS product_name, c.name AS category_name, b.name AS brand_name
                FROM size_guide_assignments a
                LEFT JOIN products p ON p.id = a.product_id
                LEFT JOIN categories c ON c.id = a.category_id
                LEFT JOIN brands b ON b.id = a.brand_id
                WHERE a.guide_id = :id ORDER BY a.scope, a.priority, a.scope_key
                """).param("id", id.toString()).query((rs, row) -> new Assignment(rs.getString("scope_key"),
                    uuid(rs, "guide_id"), Scope.valueOf(rs.getString("scope")), uuid(rs, "product_id"),
                    uuid(rs, "category_id"), uuid(rs, "brand_id"), rs.getInt("priority"),
                    rs.getString("product_name"), rs.getString("category_name"), rs.getString("brand_name"))).list();
    }

    public void assign(UUID guideId, String key, AssignmentInput input) {
        jdbc.sql("""
                INSERT INTO size_guide_assignments
                    (scope_key, guide_id, scope, product_id, category_id, brand_id, priority)
                VALUES (:key, :guide, :scope, :product, :category, :brand, :priority)
                ON DUPLICATE KEY UPDATE guide_id = VALUES(guide_id), priority = VALUES(priority)
                """).param("key", key).param("guide", guideId.toString()).param("scope", input.scope().name())
                .param("product", string(input.productId()), Types.CHAR)
                .param("category", string(input.categoryId()), Types.CHAR)
                .param("brand", string(input.brandId()), Types.CHAR)
                .param("priority", input.priority()).update();
    }

    public void unassign(UUID guideId, String key) {
        jdbc.sql("DELETE FROM size_guide_assignments WHERE scope_key = :key AND guide_id = :guide")
                .param("key", key).param("guide", guideId.toString()).update();
    }

    public boolean productExists(UUID id) {
        return exists("SELECT EXISTS (SELECT 1 FROM products WHERE id = :id AND is_deleted = FALSE)", id);
    }

    public boolean categoryExists(UUID id) {
        return exists("SELECT EXISTS (SELECT 1 FROM categories WHERE id = :id AND is_deleted = FALSE)", id);
    }

    public boolean brandExists(UUID id) {
        return exists("SELECT EXISTS (SELECT 1 FROM brands WHERE id = :id AND is_deleted = FALSE)", id);
    }

    private boolean exists(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id.toString()).query(Boolean.class).single();
    }

    public Optional<Display> resolve(UUID productId) {
        return jdbc.sql("""
                WITH catalog_item AS (
                    SELECT p.id, b.id AS brand_id
                    FROM products p
                    LEFT JOIN brands b ON b.id = p.brand_id AND b.is_deleted = FALSE AND b.status = 'ACTIVE'
                    WHERE p.id = :id AND p.is_deleted = FALSE AND p.status = 'PUBLISHED'
                ), catalog_categories AS (
                    SELECT c.id, c.sort_order, c.slug
                    FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                    WHERE pc.product_id = :id AND c.is_deleted = FALSE AND c.status = 'ACTIVE'
                ), candidates AS (
                    SELECT CONCAT('PRODUCT:', id) AS scope_key, 1 AS precedence, 0 AS category_order, '' AS category_slug
                    FROM catalog_item
                    UNION ALL
                    SELECT CONCAT('CATEGORY_BRAND:', c.id, ':', p.brand_id), 2, c.sort_order, c.slug
                    FROM catalog_categories c CROSS JOIN catalog_item p WHERE p.brand_id IS NOT NULL
                    UNION ALL
                    SELECT CONCAT('BRAND:', brand_id), 3, 0, '' FROM catalog_item WHERE brand_id IS NOT NULL
                    UNION ALL
                    SELECT CONCAT('CATEGORY:', c.id), 4, c.sort_order, c.slug
                    FROM catalog_categories c CROSS JOIN catalog_item
                    UNION ALL
                    SELECT 'SHOP', 5, 0, '' FROM catalog_item
                )
                SELECT g.id, g.name, g.content
                FROM candidates c JOIN size_guide_assignments a ON a.scope_key = c.scope_key
                JOIN size_guides g ON g.id = a.guide_id AND g.active = TRUE
                ORDER BY c.precedence, a.priority, c.category_order, c.category_slug, c.scope_key
                LIMIT 1
                """).param("id", productId.toString())
                .query((rs, row) -> new Display(uuid(rs, "id"), rs.getString("name"), read(rs.getString("content"))))
                .optional();
    }

    private Content read(String value) {
        try {
            return mapper.readValue(value, Content.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid persisted size guide", ex);
        }
    }

    private String write(Content content) {
        try {
            return mapper.writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize size guide", ex);
        }
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static String string(UUID id) {
        return id == null ? null : id.toString();
    }
}
