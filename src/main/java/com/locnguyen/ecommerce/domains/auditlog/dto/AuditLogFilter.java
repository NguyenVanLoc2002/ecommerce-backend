package com.locnguyen.ecommerce.domains.auditlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "Admin audit-log list filter")
public class AuditLogFilter {

    @Schema(example = "ORDER", description = "Filter by entity type (ORDER, PRODUCT, USER, …)")
    private String entityType;

    @Schema(example = "ORD20260408123456", description = "Filter by entity id (PK or business code)")
    private String entityId;

    @Schema(example = "ORDER_CREATED", description = "Filter by audit action enum name")
    private String action;

    @Schema(example = "admin@example.com", description = "Filter by actor (username/email — partial match)")
    private String actor;

    @Schema(example = "2026-01-01", description = "Created from date (inclusive), format: yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @Schema(example = "2026-12-31", description = "Created to date (inclusive), format: yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
}
