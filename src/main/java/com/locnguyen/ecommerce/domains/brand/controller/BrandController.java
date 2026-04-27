package com.locnguyen.ecommerce.domains.brand.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.brand.dto.BrandResponse;
import com.locnguyen.ecommerce.domains.brand.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public brand catalog endpoints (no authentication required).
 * Admin CRUD lives in {@link com.locnguyen.ecommerce.domains.admin.controller.AdminBrandController}.
 */
@Tag(name = "Brand", description = "Public brand catalog")
@RestController
@RequestMapping(AppConstants.API_V1 + "/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @Operation(summary = "List active brands")
    @GetMapping
    public ApiResponse<List<BrandResponse>> listBrands() {
        return ApiResponse.success(brandService.getActiveBrands());
    }

    @Operation(summary = "Get brand by ID")
    @GetMapping("/{id}")
    public ApiResponse<BrandResponse> getBrand(@PathVariable Long id) {
        return ApiResponse.success(brandService.getBrandById(id));
    }
}
