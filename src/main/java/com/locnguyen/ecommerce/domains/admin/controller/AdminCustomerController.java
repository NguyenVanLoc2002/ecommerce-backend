package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.admin.dto.AdminCustomerFilter;
import com.locnguyen.ecommerce.domains.admin.dto.AdminCustomerResponse;
import com.locnguyen.ecommerce.domains.admin.dto.UpdateCustomerRequest;
import com.locnguyen.ecommerce.domains.admin.dto.UpdateCustomerStatusRequest;
import com.locnguyen.ecommerce.domains.admin.service.AdminCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin - Customer Management",
        description = "Admin endpoints for browsing and managing customer profiles. " +
                "System users (STAFF/ADMIN/SUPER_ADMIN) are managed under /api/v1/admin/users.")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN', 'SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    @Operation(
            summary = "List customers with filter and pagination",
            description = "Returns paginated customer profiles joined with the linked user account. " +
                    "Supports keyword/email/phone/status/gender/loyalty/date filtering."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Paged customers returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @GetMapping
    public ApiResponse<PagedResponse<AdminCustomerResponse>> getCustomers(
            @ModelAttribute AdminCustomerFilter filter,
            @PageableDefault(
                    size = AppConstants.DEFAULT_PAGE_SIZE,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {
        return ApiResponse.success(adminCustomerService.getCustomers(filter, pageable));
    }

    @Operation(summary = "Get a customer by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Customer found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ApiResponse<AdminCustomerResponse> getCustomer(@PathVariable UUID id) {
        return ApiResponse.success(adminCustomerService.getCustomerById(id));
    }

    @Operation(
            summary = "Update a customer profile",
            description = "Partial update — only provided fields are applied. " +
                    "Updates User (firstName/lastName/phoneNumber) and Customer (gender/birthDate/avatarUrl). " +
                    "Roles cannot be modified through this endpoint; use /api/v1/admin/users for system roles."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Customer updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Phone number already registered",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges — ADMIN role required",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/{id}")
    public ApiResponse<AdminCustomerResponse> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.success(adminCustomerService.updateCustomer(id, request));
    }

    @Operation(
            summary = "Update a customer's account status",
            description = "Updates the status of the User linked to this customer (ACTIVE / INACTIVE / LOCKED). " +
                    "INACTIVE or LOCKED customers cannot authenticate."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges — ADMIN role required",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminCustomerResponse> updateCustomerStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerStatusRequest request) {
        return ApiResponse.success(adminCustomerService.updateCustomerStatus(id, request));
    }

    @Operation(
            summary = "Soft-delete a customer",
            description = "Soft-deletes the customer profile and the linked user account, " +
                    "and forces the user status to INACTIVE. Historical orders, reviews, payments, " +
                    "invoices, shipments and audit data continue to reference the rows."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Customer soft-deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges — ADMIN role required",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCustomer(@PathVariable UUID id) {
        adminCustomerService.deleteCustomer(id);
        return ApiResponse.noContent();
    }
}
