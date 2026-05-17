package com.locnguyen.ecommerce.domains.carrier.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierOptionResponse;
import com.locnguyen.ecommerce.domains.carrier.service.CarrierCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Carrier", description = "Customer carrier selection endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/carriers")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class CarrierController {

    private final CarrierCheckoutService carrierCheckoutService;

    @Operation(summary = "List carriers available for customer checkout")
    @GetMapping("/checkout-options")
    public ApiResponse<List<CheckoutCarrierOptionResponse>> getCheckoutOptions() {
        return ApiResponse.success(carrierCheckoutService.getCheckoutOptions());
    }
}
