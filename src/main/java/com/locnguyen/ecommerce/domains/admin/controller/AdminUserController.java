package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.admin.dto.AdminUserFilter;
import com.locnguyen.ecommerce.domains.admin.dto.CreateUserRequest;
import com.locnguyen.ecommerce.domains.admin.dto.UpdateUserRequest;
import com.locnguyen.ecommerce.domains.admin.service.AdminUserService;
import com.locnguyen.ecommerce.domains.auth.dto.UserResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin - User Management",
        description = "Admin endpoints for creating and managing system users (STAFF/ADMIN/SUPER_ADMIN). " +
                "Customer accounts are managed via the public auth and customer endpoints.")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "List system users with filter and pagination",
            description = "Returns paginated system users. Supports filtering by keyword, email, " +
                    "phoneNumber, status and role."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Paged users returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges — ADMIN role required",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @GetMapping
    public ApiResponse<PagedResponse<UserResponse>> getUsers(
            @ModelAttribute AdminUserFilter filter,
            @PageableDefault(
                    size = AppConstants.DEFAULT_PAGE_SIZE,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {
        return ApiResponse.success(adminUserService.getUsers(filter, pageable));
    }

    @Operation(summary = "Get a system user by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "User found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges — ADMIN role required",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID id) {
        return ApiResponse.success(adminUserService.getUserById(id));
    }

    @Operation(
            summary = "Create a system user with explicit role assignment",
            description = "Admin-only. Creates users with any role (STAFF, ADMIN, SUPER_ADMIN). " +
                    "Use the public /auth/register endpoint to create CUSTOMER accounts."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "User created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Email or phone already registered",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Insufficient privileges — ADMIN role required",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = adminUserService.createUser(request);
        return ApiResponse.created(response);
    }

    @Operation(
            summary = "Update a system user",
            description = "Partial update — only provided fields are applied. Password and email " +
                    "cannot be updated through this endpoint. Safety rules prevent removing the " +
                    "caller's own admin privileges or removing the last SUPER_ADMIN."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "User updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Phone already registered",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Forbidden — insufficient privileges or unsafe self/last-admin change",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(adminUserService.updateUser(id, request));
    }

    @Operation(
            summary = "Soft-delete (deactivate) a system user",
            description = "Soft-deletes the user via SoftDeleteEntity and sets status to INACTIVE. " +
                    "Deleted users cannot authenticate. The caller cannot delete themselves and " +
                    "the last active SUPER_ADMIN cannot be deleted."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "User soft-deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Forbidden — cannot delete self or last SUPER_ADMIN",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable UUID id) {
        adminUserService.deleteUser(id);
        return ApiResponse.noContent();
    }
}
