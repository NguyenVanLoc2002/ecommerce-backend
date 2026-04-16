package com.locnguyen.ecommerce.domains.invoice.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceResponse;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Invoices", description = "Invoice access for customers")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final UserService userService;

    @Operation(
            summary = "Get the invoice for my order",
            description = "Returns the full invoice including line items for printing or display."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/order/{orderId}")
    public ApiResponse<InvoiceResponse> getMyInvoice(@PathVariable Long orderId) {
        return ApiResponse.success(
                invoiceService.getInvoiceForCustomer(orderId, userService.getCurrentCustomer()));
    }
}
