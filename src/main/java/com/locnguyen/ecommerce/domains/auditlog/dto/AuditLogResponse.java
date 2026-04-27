package com.locnguyen.ecommerce.domains.auditlog.dto;

import com.locnguyen.ecommerce.domains.auditlog.entity.AuditLog;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {

    private final Long id;
    private final AuditAction action;
    private final String entityType;
    private final String entityId;
    private final String actor;
    private final String ipAddress;
    private final String requestId;
    private final String details;
    private final LocalDateTime createdAt;

    public static AuditLogResponse from(AuditLog entry) {
        return AuditLogResponse.builder()
                .id(entry.getId())
                .action(entry.getAction())
                .entityType(entry.getEntityType())
                .entityId(entry.getEntityId())
                .actor(entry.getActor())
                .ipAddress(entry.getIpAddress())
                .requestId(entry.getRequestId())
                .details(entry.getDetails())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
