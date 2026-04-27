package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.category.dto.CreateCategoryRequest;
import com.locnguyen.ecommerce.domains.category.dto.UpdateCategoryRequest;
import com.locnguyen.ecommerce.domains.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Category", description = "Category management for admins")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Create category")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.created(categoryService.createCategory(request));
    }

    @Operation(summary = "Update category")
    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success(categoryService.updateCategory(id, request));
    }

    @Operation(summary = "Delete category (soft)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ApiResponse.noContent();
    }
}
