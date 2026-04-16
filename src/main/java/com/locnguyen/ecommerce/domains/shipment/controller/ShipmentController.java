package com.locnguyen.ecommerce.domains.shipment.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Shipments", description = "Shipment tracking for customers")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final UserService userService;

    @Operation(
            summary = "Get the shipment tracking for my order",
            description = "Returns the full tracking timeline for the customer's own order."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/order/{orderId}")
    public ApiResponse<ShipmentResponse> getMyShipment(@PathVariable Long orderId) {
        return ApiResponse.success(
                shipmentService.getShipmentForCustomer(orderId, userService.getCurrentCustomer()));
    }
}
