package com.locnguyen.ecommerce.domains.auditlog.service;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.auditlog.dto.AuditLogFilter;
import com.locnguyen.ecommerce.domains.auditlog.dto.AuditLogResponse;
import com.locnguyen.ecommerce.domains.auditlog.entity.AuditLog;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.repository.AuditLogRepository;
import com.locnguyen.ecommerce.domains.auditlog.specification.AuditLogSpecification;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Async audit-log writer.
 *
 * <p>Every public method runs on the dedicated {@code auditLogExecutor} thread pool
 * (configured in {@link com.locnguyen.ecommerce.common.config.AsyncConfig}) with its
 * own transaction ({@link Propagation#REQUIRES_NEW}).
 *
 * <p>Using {@code REQUIRES_NEW} means:
 * <ul>
 *   <li>If the calling transaction commits, the audit row is independently committed.</li>
 *   <li>If the calling transaction rolls back (e.g., order creation fails), the audit
 *       row is still written — preserving the "attempted" record for compliance.</li>
 * </ul>
 *
 * <p>Callers should never {@code throw} based on the return value of these methods.
 * Any exception is swallowed here to prevent audit failures from disrupting business flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // ─── Primary entry points ────────────────────────────────────────────────

    /**
     * Record an audit event for a domain entity.
     *
     * @param action     what happened
     * @param entityType high-level domain (ORDER, PRODUCT, USER …)
     * @param entityId   PK or business code of the affected row
     * @param details    optional description or JSON diff
     */
    @Async("auditLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, String entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId != null ? entityId : "unknown");
            entry.setActor(SecurityUtils.getCurrentUsernameOrSystem());
            entry.setIpAddress(resolveClientIp());
            entry.setRequestId(MDC.get(AppConstants.MDC_REQUEST_ID));
            entry.setDetails(details);
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.error("Failed to persist audit log: action={} entityType={} entityId={} — {}",
                    action, entityType, entityId, ex.getMessage());
        }
    }

    /**
     * Convenience overload — no detail message.
     */
    @Async("auditLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, String entityId) {
        log(action, entityType, entityId, null);
    }

    /**
     * Log an event with an explicit actor (e.g., for login-failure where there is
     * no authenticated principal in the SecurityContext yet).
     */
    @Async("auditLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logWithActor(AuditAction action, String entityType, String entityId,
                             String actor, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId != null ? entityId : "unknown");
            entry.setActor(actor != null ? actor : AppConstants.SYSTEM_USER);
            entry.setIpAddress(resolveClientIp());
            entry.setRequestId(MDC.get(AppConstants.MDC_REQUEST_ID));
            entry.setDetails(details);
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.error("Failed to persist audit log (explicit actor): action={} actor={} — {}",
                    action, actor, ex.getMessage());
        }
    }

    // ─── Read API (admin) ────────────────────────────────────────────────────

    /**
     * List audit logs with filter and pagination, ordered by createdAt DESC.
     *
     * <p>Synchronous read — runs on the caller's thread inside its transaction.
     */
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> getAuditLogs(AuditLogFilter filter, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAll(
                AuditLogSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(AuditLogResponse::from));
    }

    /**
     * Get a single audit log entry by id.
     */
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(Long id) {
        return auditLogRepository.findById(id)
                .map(AuditLogResponse::from)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Audit log not found: id=" + id));
    }

    // ─── IP resolution ───────────────────────────────────────────────────────

    /**
     * Extract the real client IP, honouring common reverse-proxy headers.
     * Returns {@code null} when called outside a web request context (e.g., in a
     * scheduled task).
     */
    private String resolveClientIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
                return null;
            }
            HttpServletRequest req = servletAttrs.getRequest();

            // Standard reverse-proxy headers, checked in preference order
            String[] headers = {
                    "X-Forwarded-For",
                    "X-Real-IP",
                    "Proxy-Client-IP",
                    "WL-Proxy-Client-IP"
            };
            for (String header : headers) {
                String value = req.getHeader(header);
                if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                    // X-Forwarded-For may contain a chain; take the first (original client) IP
                    return value.split(",")[0].trim();
                }
            }
            return req.getRemoteAddr();
        } catch (Exception ex) {
            return null;
        }
    }
}
