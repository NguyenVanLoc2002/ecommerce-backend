package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.*;
import com.locnguyen.ecommerce.domains.carrier.service.CarrierService;
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

import java.util.UUID;

@Tag(name = "Admin - Carriers", description = "Carrier catalog and store configuration")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/carriers")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminCarrierController {

    private final CarrierService carrierService;

    @Operation(summary = "List carriers")
    @GetMapping
    public ApiResponse<PagedResponse<CarrierResponse>> getCarriers(
            @ModelAttribute CarrierFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(carrierService.getCarriers(filter, pageable));
    }

    @Operation(summary = "Get carrier by ID")
    @GetMapping("/{id}")
    public ApiResponse<CarrierResponse> getCarrierById(@PathVariable UUID id) {
        return ApiResponse.success(carrierService.getCarrierById(id));
    }

    @Operation(summary = "Create carrier")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<CarrierResponse> createCarrier(@Valid @RequestBody CreateCarrierRequest request) {
        return ApiResponse.created(carrierService.createCarrier(request));
    }

    @Operation(summary = "Update carrier")
    @PatchMapping("/{id}")
    public ApiResponse<CarrierResponse> updateCarrier(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCarrierRequest request) {
        return ApiResponse.success(carrierService.updateCarrier(id, request));
    }

    @Operation(summary = "Save carrier config")
    @PutMapping("/{id}/config")
    public ApiResponse<CarrierResponse> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCarrierConfigRequest request) {
        return ApiResponse.success(carrierService.updateConfig(id, request));
    }

    @Operation(summary = "Enable or disable a carrier")
    @PatchMapping("/{id}/toggle")
    public ApiResponse<CarrierResponse> toggleCarrier(
            @PathVariable UUID id,
            @Valid @RequestBody ToggleCarrierRequest request) {
        return ApiResponse.success(carrierService.toggleCarrier(id, request));
    }

    @Operation(summary = "Get typed AhaMove integration config")
    @GetMapping("/{id}/integration/ahamove")
    public ApiResponse<AhamoveIntegrationResponse> getAhamoveIntegration(@PathVariable UUID id) {
        return ApiResponse.success(carrierService.getAhamoveIntegration(id));
    }

    @Operation(summary = "Save typed AhaMove integration config")
    @PutMapping("/{id}/integration/ahamove")
    public ApiResponse<AhamoveIntegrationResponse> updateAhamoveIntegration(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAhamoveIntegrationRequest request) {
        return ApiResponse.success(carrierService.updateAhamoveIntegration(id, request));
    }

    @Operation(summary = "Test AhaMove API connection")
    @PostMapping("/{id}/integration/ahamove/test-connection")
    public ApiResponse<AhamoveConnectionTestResponse> testAhamoveConnection(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) TestAhamoveConnectionRequest request) {
        return ApiResponse.success(carrierService.testAhamoveConnection(
                id, request != null ? request : new TestAhamoveConnectionRequest()));
    }

    @Operation(summary = "Generate and save a new AhaMove webhook token")
    @PostMapping("/{id}/integration/ahamove/webhook-token")
    public ApiResponse<AhamoveWebhookTokenResponse> generateAhamoveWebhookToken(@PathVariable UUID id) {
        return ApiResponse.success(carrierService.generateAhamoveWebhookToken(id));
    }

    @Operation(summary = "Get AhaMove webhook setup instructions")
    @GetMapping("/{id}/integration/ahamove/webhook-setup")
    public ApiResponse<AhamoveWebhookSetupResponse> getAhamoveWebhookSetup(@PathVariable UUID id) {
        return ApiResponse.success(carrierService.getAhamoveWebhookSetup(id));
    }
}
