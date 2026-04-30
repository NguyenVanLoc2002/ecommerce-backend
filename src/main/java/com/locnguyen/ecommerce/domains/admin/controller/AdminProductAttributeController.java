package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.product.dto.attribute.CreateProductAttributeRequest;
import com.locnguyen.ecommerce.domains.product.dto.attribute.CreateProductAttributeValueRequest;
import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeFilter;
import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeResponse;
import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeValueResponse;
import com.locnguyen.ecommerce.domains.product.dto.attribute.UpdateProductAttributeRequest;
import com.locnguyen.ecommerce.domains.product.service.ProductAttributeService;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * Admin management for reusable product attributes ({@code Color}, {@code Size}, …)
 * and their values. The same listing endpoint, filtered by {@code type=VARIANT},
 * is what the variant form uses to render attribute pickers.
 */
@Tag(name = "Admin — Product Attribute", description = "Reusable product attributes and values")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/product-attributes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class AdminProductAttributeController {

    private final ProductAttributeService attributeService;

    @Operation(summary = "List product attributes (filterable by type and keyword)")
    @GetMapping
    public ApiResponse<PagedResponse<ProductAttributeResponse>> getAttributes(
            ProductAttributeFilter filter,
            @PageableDefault(
                    size = AppConstants.DEFAULT_PAGE_SIZE,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {
        return ApiResponse.success(attributeService.getAttributes(filter, pageable));
    }

    @Operation(summary = "Get a single product attribute by id")
    @GetMapping("/{id}")
    public ApiResponse<ProductAttributeResponse> getAttribute(@PathVariable UUID id) {
        return ApiResponse.success(attributeService.getAttribute(id));
    }

    @Operation(summary = "Create a product attribute (optionally seed values)")
    @PostMapping
    public ApiResponse<ProductAttributeResponse> createAttribute(
            @Valid @RequestBody CreateProductAttributeRequest request) {
        return ApiResponse.created(attributeService.createAttribute(request));
    }

    @Operation(summary = "Update a product attribute (partial). 'values' replaces the value set if present.")
    @PutMapping("/{id}")
    public ApiResponse<ProductAttributeResponse> updateAttribute(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductAttributeRequest request) {
        return ApiResponse.success(attributeService.updateAttribute(id, request));
    }

    @Operation(summary = "Soft-delete a product attribute (and all its values)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAttribute(@PathVariable UUID id) {
        attributeService.deleteAttribute(id);
        return ApiResponse.noContent();
    }

    // ─── Value-level endpoints ──────────────────────────────────────────────

    @Operation(summary = "Add a single value to an attribute")
    @PostMapping("/{attributeId}/values")
    public ApiResponse<ProductAttributeValueResponse> addValue(
            @PathVariable UUID attributeId,
            @Valid @RequestBody CreateProductAttributeValueRequest request) {
        return ApiResponse.created(attributeService.addValue(attributeId, request));
    }

    @Operation(summary = "Update a single attribute value")
    @PutMapping("/{attributeId}/values/{valueId}")
    public ApiResponse<ProductAttributeValueResponse> updateValue(
            @PathVariable UUID attributeId,
            @PathVariable UUID valueId,
            @Valid @RequestBody CreateProductAttributeValueRequest request) {
        return ApiResponse.success(attributeService.updateValue(attributeId, valueId, request));
    }

    @Operation(summary = "Soft-delete a single attribute value (forbidden if used by any variant)")
    @DeleteMapping("/{attributeId}/values/{valueId}")
    public ApiResponse<Void> deleteValue(
            @PathVariable UUID attributeId,
            @PathVariable UUID valueId) {
        attributeService.deleteValue(attributeId, valueId);
        return ApiResponse.noContent();
    }
}
