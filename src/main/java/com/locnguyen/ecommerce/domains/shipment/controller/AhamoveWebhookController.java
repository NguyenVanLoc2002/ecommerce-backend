package com.locnguyen.ecommerce.domains.shipment.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Shipment Webhooks", description = "Carrier webhook receivers")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/shipments/webhook")
public class AhamoveWebhookController {

    private final AhamoveWebhookService ahamoveWebhookService;

    @Operation(summary = "[AhaMove] Receive carrier status callback")
    @PostMapping("/ahamove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveAhamoveWebhook(
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers) {
        ahamoveWebhookService.receiveWebhook(rawBody, headers);
    }
}
