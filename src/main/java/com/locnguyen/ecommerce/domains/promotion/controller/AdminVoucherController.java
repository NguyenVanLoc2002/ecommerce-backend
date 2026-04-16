package com.locnguyen.ecommerce.domains.promotion.controller;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Vouchers", description = "Voucher management (admin only)")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/vouchers")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminVoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Create a voucher for a promotion")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<VoucherResponse> create(@Valid @RequestBody CreateVoucherRequest request) {
        return ApiResponse.created(voucherService.createVoucher(request));
    }

    @Operation(summary = "Get a voucher by ID")
    @GetMapping("/{id}")
    public ApiResponse<VoucherResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(voucherService.getById(id));
    }

    @Operation(summary = "Get a voucher by code")
    @GetMapping("/code/{code}")
    public ApiResponse<VoucherResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success(voucherService.getByCode(code));
    }

    @Operation(summary = "List vouchers (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<VoucherResponse>> list(
            VoucherFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));
        return ApiResponse.success(voucherService.listVouchers(filter, pageable));
    }

    @Operation(summary = "Update a voucher")
    @PatchMapping("/{id}")
    public ApiResponse<VoucherResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVoucherRequest request) {
        return ApiResponse.success(voucherService.updateVoucher(id, request));
    }

    @Operation(summary = "Delete (soft-delete) a voucher")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ApiResponse.noContent();
    }

    @Operation(summary = "Get usage history for a voucher")
    @GetMapping("/{id}/usages")
    public ApiResponse<PagedResponse<VoucherUsageResponse>> getUsages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(voucherService.getUsages(id, pageable));
    }
}
