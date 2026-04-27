package com.locnguyen.ecommerce.domains.category.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public category catalog endpoints (no authentication required).
 * Admin CRUD lives in {@link com.locnguyen.ecommerce.domains.admin.controller.AdminCategoryController}.
 */
@Tag(name = "Category", description = "Public product category catalog")
@RestController
@RequestMapping(AppConstants.API_V1 + "/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "List active categories")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.success(categoryService.getActiveCategories());
    }

    @Operation(summary = "Get category by ID")
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategory(@PathVariable Long id) {
        return ApiResponse.success(categoryService.getCategoryById(id));
    }
}
