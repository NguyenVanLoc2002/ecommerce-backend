package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.product.dto.*;
import com.locnguyen.ecommerce.domains.product.service.ProductService;
import com.locnguyen.ecommerce.domains.productvariant.service.ProductVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin product management API.
 *
 * <p>All endpoints require ADMIN or SUPER_ADMIN role (enforced at URL level by SecurityConfig
 * and explicitly via {@code @PreAuthorize} for defence-in-depth).
 *
 * <p>Business logic stays in {@link ProductService} and {@link ProductVariantService};
 * this controller is a pure routing layer.
 */
@Tag(name = "Admin — Product", description = "Product and variant management for admins")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class AdminProductController {

    private final ProductService productService;
    private final ProductVariantService variantService;

    // ─── Product CRUD ─────────────────────────────────────────────────────────

    @Operation(summary = "List all products including drafts (filtered, paginated)")
    @GetMapping
    public ApiResponse<PagedResponse<ProductListItemResponse>> getAllProducts(
            ProductFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "createdAt,desc") Pageable pageable) {
        return ApiResponse.success(productService.getAllProducts(filter, pageable));
    }

    @Operation(summary = "Get any product detail by ID (no status filter)")
    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.success(productService.getProductDetailAdmin(id));
    }

    @Operation(summary = "Create product")
    @PostMapping
    public ApiResponse<ProductDetailResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.created(productService.createProduct(request));
    }

    @Operation(summary = "Update product")
    @PatchMapping("/{id}")
    public ApiResponse<ProductDetailResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(productService.updateProduct(id, request));
    }

    @Operation(summary = "Soft-delete product")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.noContent();
    }

    // ─── Variant management ───────────────────────────────────────────────────

    @Operation(summary = "List variants for a product")
    @GetMapping("/{productId}/variants")
    public ApiResponse<List<VariantResponse>> getVariants(@PathVariable Long productId) {
        return ApiResponse.success(variantService.getVariantsByProduct(productId));
    }

    @Operation(summary = "Create variant for a product")
    @PostMapping("/{productId}/variants")
    public ApiResponse<VariantResponse> createVariant(
            @PathVariable Long productId,
            @Valid @RequestBody CreateVariantRequest request) {
        return ApiResponse.created(variantService.createVariant(productId, request));
    }

    @Operation(summary = "Update variant")
    @PatchMapping("/{productId}/variants/{variantId}")
    public ApiResponse<VariantResponse> updateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateVariantRequest request) {
        return ApiResponse.success(variantService.updateVariant(productId, variantId, request));
    }

    @Operation(summary = "Soft-delete variant")
    @DeleteMapping("/{productId}/variants/{variantId}")
    public ApiResponse<Void> deleteVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId) {
        variantService.deleteVariant(productId, variantId);
        return ApiResponse.noContent();
    }
}
