package com.locnguyen.ecommerce.domains.auditlog.entity;

import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Immutable audit trail record.
 *
 * <p>Does NOT extend {@link com.locnguyen.ecommerce.common.auditing.BaseEntity}:
 * <ul>
 *   <li>No {@code updatedAt} / {@code updatedBy} — rows are never modified</li>
 *   <li>No {@code AuditingEntityListener} — avoids a circular dependency where
 *       auditing the auditor would produce infinite recursion</li>
 * </ul>
 * {@code createdAt} is set explicitly in
 * {@link com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService}.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 100, nullable = false)
    private AuditAction action;

    /** High-level domain name: ORDER, PRODUCT, USER, etc. */
    @Column(name = "entity_type", length = 100, nullable = false)
    private String entityType;

    /** Primary key or business code of the affected row. */
    @Column(name = "entity_id", length = 100, nullable = false)
    private String entityId;

    /** Username from SecurityContext — falls back to "system" for background jobs. */
    @Column(name = "actor", length = 100, nullable = false)
    private String actor;

    /** Caller IP — extracted from {@code X-Forwarded-For} first, then REMOTE_ADDR. */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** Correlates to the {@code X-Request-ID} propagated by RequestLoggingFilter. */
    @Column(name = "request_id", length = 100)
    private String requestId;

    /** Optional human-readable description or serialised JSON diff. */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
