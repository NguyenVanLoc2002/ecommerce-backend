package com.locnguyen.ecommerce.domains.shippingpolicy;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.shippingpolicy.ShippingPolicyModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingPolicyService {
    private static final int PAGE_SIZE = 20;

    private final JdbcClient jdbc;
    private final ShippingPolicyAccess access;

    @Transactional(readOnly = true)
    public PublicPolicy published() {
        if (!access.publicationEnabled()) {
            throw new AppException(ErrorCode.NOT_FOUND);
        }
        return jdbc.sql("""
                SELECT version, title, content, status, effective_from, published_at
                FROM shipping_policy_versions
                WHERE effective_from <= :now AND published_at IS NOT NULL
                  AND (archived_at IS NULL OR archived_at >= effective_from)
                ORDER BY effective_from DESC, version DESC LIMIT 1
                """)
                .param("now", utc(Instant.now()))
                .query((row, index) -> "PUBLISHED".equals(row.getString("status"))
                        ? new PublicPolicy(row.getLong("version"), row.getString("title"),
                            row.getString("content"), instant(row, "effective_from"), instant(row, "published_at"))
                        : null)
                .optional()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Capabilities capabilities() {
        return access.capabilities();
    }

    @Transactional(readOnly = true)
    public Page list(int page) {
        access.requireRead();
        var items = jdbc.sql("""
                SELECT version, revision, title, status, effective_from, published_at
                FROM shipping_policy_versions ORDER BY version DESC LIMIT :limit OFFSET :offset
                """)
                .param("limit", PAGE_SIZE + 1)
                .param("offset", (long) page * PAGE_SIZE)
                .query((row, index) -> new Summary(row.getLong("version"), row.getInt("revision"),
                        row.getString("title"), Status.valueOf(row.getString("status")),
                        instant(row, "effective_from"), instant(row, "published_at")))
                .list();
        return new Page(firstPage(items), items.size() > PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Policy get(long version) {
        access.requireRead();
        return load(version);
    }

    @Transactional(readOnly = true)
    public HistoryPage history(long version, int page) {
        access.requireRead();
        var items = jdbc.sql("""
                SELECT revision, title, content, status, effective_from, actor, changed_at
                FROM shipping_policy_history WHERE version = :version
                ORDER BY revision DESC LIMIT :limit OFFSET :offset
                """)
                .param("version", version)
                .param("limit", PAGE_SIZE + 1)
                .param("offset", (long) page * PAGE_SIZE)
                .query((row, index) -> new Change(row.getInt("revision"), row.getString("title"),
                        row.getString("content"), Status.valueOf(row.getString("status")),
                        instant(row, "effective_from"), UUID.fromString(row.getString("actor")),
                        instant(row, "changed_at")))
                .list();
        return new HistoryPage(firstPage(items), items.size() > PAGE_SIZE);
    }

    @Transactional
    public Policy create(Draft input) {
        UUID actor = access.requireEdit();
        var key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO shipping_policy_versions (title, content, status, effective_from)
                VALUES (:title, :content, 'DRAFT', :effective)
                """)
                .param("title", input.title().strip())
                .param("content", input.content().strip())
                .param("effective", utc(input.effectiveFrom()))
                .update(key, "version");
        return recordChange(key.getKey().longValue(), actor, Instant.now());
    }

    @Transactional
    public Policy edit(long version, Edit input) {
        UUID actor = access.requireEdit();
        Draft draft = input.draft();
        requireUpdated(jdbc.sql("""
                UPDATE shipping_policy_versions
                SET title = :title, content = :content, effective_from = :effective, revision = revision + 1
                WHERE version = :version AND revision = :revision AND status = 'DRAFT'
                """)
                .param("title", draft.title().strip())
                .param("content", draft.content().strip())
                .param("effective", utc(draft.effectiveFrom()))
                .param("version", version)
                .param("revision", input.revision())
                .update());
        return recordChange(version, actor, Instant.now());
    }

    @Transactional
    public Policy publish(long version, int revision) {
        UUID actor = access.requirePublish();
        if (!access.publicationEnabled()) {
            throw new AppException(ErrorCode.CONFLICT, "Shipping policy publication is disabled");
        }
        Instant now = Instant.now();
        requireUpdated(jdbc.sql("""
                UPDATE shipping_policy_versions
                SET status = 'PUBLISHED', revision = revision + 1, published_at = :now, published_by = :actor,
                    effective_from = GREATEST(effective_from, :now)
                WHERE version = :version AND revision = :revision AND status = 'DRAFT'
                """)
                .param("version", version)
                .param("revision", revision)
                .param("now", utc(now))
                .param("actor", actor.toString())
                .update());
        return recordChange(version, actor, now);
    }

    @Transactional
    public Policy archive(long version, int revision) {
        UUID actor = access.requirePublish();
        Instant now = Instant.now();
        requireUpdated(jdbc.sql("""
                UPDATE shipping_policy_versions
                SET status = 'ARCHIVED', revision = revision + 1, archived_at = :now
                WHERE version = :version AND revision = :revision AND status <> 'ARCHIVED'
                """)
                .param("version", version)
                .param("revision", revision)
                .param("now", utc(now))
                .update());
        return recordChange(version, actor, now);
    }

    private Policy recordChange(long version, UUID actor, Instant now) {
        jdbc.sql("""
                INSERT INTO shipping_policy_history
                (version, revision, title, content, status, effective_from, actor, changed_at)
                SELECT version, revision, title, content, status, effective_from, :actor, :now
                FROM shipping_policy_versions WHERE version = :version
                """)
                .param("version", version)
                .param("actor", actor.toString())
                .param("now", utc(now))
                .update();
        return load(version);
    }

    private Policy load(long version) {
        return jdbc.sql("SELECT * FROM shipping_policy_versions WHERE version = :version")
                .param("version", version)
                .query((row, index) -> {
                    String publisher = row.getString("published_by");
                    return new Policy(row.getLong("version"), row.getInt("revision"), row.getString("title"),
                            row.getString("content"), Status.valueOf(row.getString("status")),
                            instant(row, "effective_from"), instant(row, "published_at"),
                            publisher == null ? null : UUID.fromString(publisher));
                })
                .optional()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    private static void requireUpdated(int count) {
        if (count != 1) {
            throw new AppException(ErrorCode.CONFLICT, "The policy status or revision changed. Reload the policy.");
        }
    }

    private static <T> List<T> firstPage(List<T> results) {
        return List.copyOf(results.subList(0, Math.min(PAGE_SIZE, results.size())));
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        LocalDateTime value = row.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
