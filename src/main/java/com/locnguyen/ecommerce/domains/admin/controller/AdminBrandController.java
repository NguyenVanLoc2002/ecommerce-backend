package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.brand.dto.BrandFilter;
import com.locnguyen.ecommerce.domains.brand.dto.BrandResponse;
import com.locnguyen.ecommerce.domains.brand.dto.CreateBrandRequest;
import com.locnguyen.ecommerce.domains.brand.dto.UpdateBrandRequest;
import com.locnguyen.ecommerce.domains.brand.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Brand", description = "Brand management for admins")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/brands")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminBrandController {

    private final BrandService brandService;

    @Operation(summary = "List all brands with filter and pagination")
    @GetMapping
    public ApiResponse<PagedResponse<BrandResponse>> getBrands(
            @ModelAttribute BrandFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "sortOrder") Pageable pageable) {
        return ApiResponse.success(brandService.getBrands(filter, pageable));
    }

    @Operation(summary = "Create brand")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<BrandResponse> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {
        return ApiResponse.created(brandService.createBrand(request));
    }

    @Operation(summary = "Update brand")
    @PatchMapping("/{id}")
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request) {
        return ApiResponse.success(brandService.updateBrand(id, request));
    }

    @Operation(summary = "Delete brand (soft)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return ApiResponse.noContent();
    }
}
