package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.catalogasset.dto.CatalogAssetResponse;
import com.locnguyen.ecommerce.domains.catalogasset.dto.CompleteCatalogUploadRequest;
import com.locnguyen.ecommerce.domains.catalogasset.dto.CreateCatalogUploadRequest;
import com.locnguyen.ecommerce.domains.catalogasset.dto.PresignedCatalogUploadResponse;
import com.locnguyen.ecommerce.domains.catalogasset.service.CatalogAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Catalog uploads", description = "Direct catalog-image upload workflow")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/catalog-uploads")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminCatalogUploadController {

    private final CatalogAssetService catalogAssetService;

    @Operation(summary = "Create a short-lived presigned PUT URL for a catalog image")
    @PostMapping
    public ApiResponse<PresignedCatalogUploadResponse> requestUpload(@Valid @RequestBody CreateCatalogUploadRequest request) {
        return ApiResponse.created(catalogAssetService.requestUpload(request));
    }

    @Operation(summary = "Verify an uploaded catalog image before it can be attached to product media")
    @PostMapping("/complete")
    public ApiResponse<CatalogAssetResponse> completeUpload(@Valid @RequestBody CompleteCatalogUploadRequest request) {
        return ApiResponse.success(catalogAssetService.completeUpload(request.getStorageKey()));
    }
}
