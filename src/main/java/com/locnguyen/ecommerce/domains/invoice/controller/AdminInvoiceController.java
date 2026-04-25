package com.locnguyen.ecommerce.domains.invoice.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceFilter;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceResponse;
import com.locnguyen.ecommerce.domains.invoice.dto.UpdateInvoiceStatusRequest;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Invoices", description = "Invoice generation and management (admin only)")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/invoices")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'STAFF')")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;

    @Operation(
            summary = "Generate an invoice for an order",
            description = "Idempotent — returns the existing invoice if one already exists. " +
                    "Order must be in CONFIRMED or a later active status."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/order/{orderId}/generate")
    public ApiResponse<InvoiceResponse> generate(@PathVariable Long orderId) {
        return ApiResponse.created(invoiceService.generateInvoice(orderId));
    }

    @Operation(summary = "Get an invoice by ID")
    @GetMapping("/{id}")
    public ApiResponse<InvoiceResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(invoiceService.getById(id));
    }

    @Operation(summary = "Get an invoice by order ID")
    @GetMapping("/order/{orderId}")
    public ApiResponse<InvoiceResponse> getByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(invoiceService.getByOrderId(orderId));
    }

    @Operation(summary = "Get an invoice by invoice code")
    @GetMapping("/code/{invoiceCode}")
    public ApiResponse<InvoiceResponse> getByCode(@PathVariable String invoiceCode) {
        return ApiResponse.success(invoiceService.getByCode(invoiceCode));
    }

    @Operation(summary = "List invoices (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<InvoiceResponse>> getInvoices(
            InvoiceFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(invoiceService.getInvoices(filter, pageable));
    }

    @Operation(
            summary = "Update invoice status",
            description = "Allowed transitions from ISSUED: → PAID, → VOIDED. " +
                    "PAID and VOIDED are terminal."
    )
    @PatchMapping("/{id}/status")
    public ApiResponse<InvoiceResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInvoiceStatusRequest request) {
        return ApiResponse.success(invoiceService.updateStatus(id, request));
    }
}
