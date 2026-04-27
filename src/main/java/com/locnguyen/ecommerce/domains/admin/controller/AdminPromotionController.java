package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.*;
import com.locnguyen.ecommerce.domains.promotion.service.PromotionService;
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

@Tag(name = "Admin — Promotions", description = "Promotion and rule management (admin only)")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/promotions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @Operation(summary = "Create a promotion")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<PromotionResponse> create(@Valid @RequestBody CreatePromotionRequest request) {
        return ApiResponse.created(promotionService.createPromotion(request));
    }

    @Operation(summary = "Get a promotion by ID")
    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(promotionService.getById(id));
    }

    @Operation(summary = "List promotions (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<PromotionResponse>> getPromotions(
            PromotionFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(promotionService.getPromotions(filter, pageable));
    }

    @Operation(summary = "Update a promotion")
    @PatchMapping("/{id}")
    public ApiResponse<PromotionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePromotionRequest request) {
        return ApiResponse.success(promotionService.updatePromotion(id, request));
    }

    @Operation(summary = "Delete (soft-delete) a promotion")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ApiResponse.noContent();
    }

    @Operation(summary = "Add a rule to a promotion")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{id}/rules")
    public ApiResponse<PromotionResponse> addRule(
            @PathVariable Long id,
            @Valid @RequestBody AddRuleRequest request) {
        return ApiResponse.created(promotionService.addRule(id, request));
    }

    @Operation(summary = "Remove a rule from a promotion")
    @DeleteMapping("/{id}/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<PromotionResponse> removeRule(
            @PathVariable Long id,
            @PathVariable Long ruleId) {
        return ApiResponse.success(promotionService.removeRule(id, ruleId));
    }
}
