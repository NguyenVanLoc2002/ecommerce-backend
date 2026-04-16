package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.*;
import com.locnguyen.ecommerce.domains.promotion.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin voucher management API.
 *
 * <p>Voucher lifecycle (create, update, delete) is restricted to ADMIN and above —
 * STAFF can view but not mutate.
 *
 * <p>All business logic is delegated to {@link VoucherService}.
 */
@Tag(name = "Admin — Voucher", description = "Voucher management")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/vouchers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminVoucherController {

    private final VoucherService voucherService;

    // ─── Read operations ──────────────────────────────────────────────────────

    @Operation(summary = "Get voucher by ID")
    @GetMapping("/{id}")
    public ApiResponse<VoucherResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(voucherService.getById(id));
    }

    @Operation(summary = "Get voucher by code")
    @GetMapping("/code/{code}")
    public ApiResponse<VoucherResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success(voucherService.getByCode(code));
    }

    @Operation(summary = "List vouchers (filterable, paginated)")
    @GetMapping
    public ApiResponse<PagedResponse<VoucherResponse>> list(
            VoucherFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(voucherService.listVouchers(filter, pageable));
    }

    @Operation(summary = "Get usage history for a voucher")
    @GetMapping("/{id}/usages")
    public ApiResponse<PagedResponse<VoucherUsageResponse>> getUsages(
            @PathVariable Long id,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(voucherService.getUsages(id, pageable));
    }

    // ─── Mutating operations (ADMIN and above) ────────────────────────────────

    @Operation(summary = "Create a voucher for a promotion")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<VoucherResponse> create(@Valid @RequestBody CreateVoucherRequest request) {
        return ApiResponse.created(voucherService.createVoucher(request));
    }

    @Operation(summary = "Update a voucher")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<VoucherResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVoucherRequest request) {
        return ApiResponse.success(voucherService.updateVoucher(id, request));
    }

    @Operation(summary = "Soft-delete a voucher")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ApiResponse.noContent();
    }
}
