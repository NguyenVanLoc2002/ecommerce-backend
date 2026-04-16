-- =====================================================
-- V12__create_audit_log_table.sql
-- Immutable audit trail for sensitive business events.
-- No soft-delete, no updatedAt — rows are never modified.
-- =====================================================

CREATE TABLE audit_logs (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- What happened
    action       VARCHAR(100)  NOT NULL,   -- e.g. ORDER_CONFIRMED, PRODUCT_DELETED
    entity_type  VARCHAR(100)  NOT NULL,   -- e.g. ORDER, PRODUCT, USER
    entity_id    VARCHAR(100)  NOT NULL,   -- PK or business code of the affected entity

    -- Who & where
    actor        VARCHAR(100)  NOT NULL,   -- username from SecurityContext or "system"
    ip_address   VARCHAR(50)   NULL,       -- extracted from X-Forwarded-For or REMOTE_ADDR

    -- Correlation
    request_id   VARCHAR(100)  NULL,       -- X-Request-ID from RequestLoggingFilter MDC

    -- Detail payload
    details      TEXT          NULL,       -- free-form description or JSON

    -- Timestamp (immutable)
    created_at   DATETIME      NOT NULL
);

CREATE INDEX idx_audit_logs_action      ON audit_logs(action);
CREATE INDEX idx_audit_logs_entity      ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor       ON audit_logs(actor);
CREATE INDEX idx_audit_logs_created_at  ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_request_id  ON audit_logs(request_id);
