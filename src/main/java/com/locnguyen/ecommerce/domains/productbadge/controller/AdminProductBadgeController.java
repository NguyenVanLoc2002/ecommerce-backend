package com.locnguyen.ecommerce.domains.productbadge.controller;

import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels.*;
import com.locnguyen.ecommerce.domains.productbadge.service.ProductBadgeService;
import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/product-badges")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminProductBadgeController {
    private final ProductBadgeService service;

    @GetMapping
    public ApiResponse<PagedResponse<Badge>> list(@PageableDefault(size = 20) Pageable page) {
        return ApiResponse.success(service.list(page));
    }

    @GetMapping("/{id}")
    public ApiResponse<Badge> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<Badge> create(@RequestBody @Valid Input input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Badge> update(@PathVariable UUID id, @RequestBody @Valid Input input) {
        return ApiResponse.success(service.update(id, input));
    }

    @GetMapping("/{id}/products")
    public ApiResponse<PagedResponse<ProductReference>> products(@PathVariable UUID id, @PageableDefault(size = 20) Pageable page) {
        return ApiResponse.success(service.products(id, page));
    }

    @PutMapping("/{id}/products/{productId}")
    public ApiResponse<Void> assign(@PathVariable UUID id, @PathVariable UUID productId) {
        service.assign(id, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/products/{productId}")
    public ApiResponse<Void> unassign(@PathVariable UUID id, @PathVariable UUID productId) {
        service.unassign(id, productId);
        return ApiResponse.success(null);
    }
}
