package com.locnguyen.ecommerce.domains.product.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.product.dto.ProductDetailResponse;
import com.locnguyen.ecommerce.domains.product.dto.ProductFilter;
import com.locnguyen.ecommerce.domains.product.dto.ProductListItemResponse;
import com.locnguyen.ecommerce.domains.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * Public product catalog endpoints (no authentication required).
 * Admin CRUD lives in {@link com.locnguyen.ecommerce.domains.admin.controller.AdminProductController}.
 */
@Tag(name = "Product", description = "Public product catalog")
@RestController
@RequestMapping(AppConstants.API_V1 + "/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "List published products with filters")
    @GetMapping
    public ApiResponse<PagedResponse<ProductListItemResponse>> getProducts(
            ProductFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "createdAt,desc") Pageable pageable) {
        return ApiResponse.success(productService.getPublishedProducts(filter, pageable));
    }

    @Operation(summary = "Get published product detail with variants and media")
    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.success(productService.getProductById(id));
    }
}
