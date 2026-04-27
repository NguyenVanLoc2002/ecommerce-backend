package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.auditlog.dto.AuditLogFilter;
import com.locnguyen.ecommerce.domains.auditlog.dto.AuditLogResponse;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin audit-log query API.
 *
 * <p>Read-only — audit entries are written asynchronously by
 * {@link AuditLogService} from the rest of the codebase. They are never
 * mutated or deleted via the API.
 *
 * <p>Restricted to ADMIN and SUPER_ADMIN — STAFF cannot view audit history.
 */
@Tag(name = "Admin — Audit Log", description = "Audit-log query for admins")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAuditController {

    private final AuditLogService auditLogService;

    @Operation(summary = "List audit logs (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<AuditLogResponse>> getAuditLogs(
            AuditLogFilter filter,
            @PageableDefault(
                    size = AppConstants.DEFAULT_PAGE_SIZE,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {
        return ApiResponse.success(auditLogService.getAuditLogs(filter, pageable));
    }

    @Operation(summary = "Get audit log entry by ID")
    @GetMapping("/{id}")
    public ApiResponse<AuditLogResponse> getAuditLog(@PathVariable Long id) {
        return ApiResponse.success(auditLogService.getAuditLogById(id));
    }
}
