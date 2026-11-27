package com.locnguyen.ecommerce.domains.invoice.tax;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.invoice.tax.TaxInvoiceDocuments.*;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/tax/invoices/{invoiceId}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class TaxInvoiceController {
    private final TaxInvoiceDocuments documents;

    @GetMapping("/preparation")
    public ApiResponse<Preparation> preparation(@PathVariable UUID invoiceId) {
        return ApiResponse.success(documents.preparation(invoiceId));
    }

    @GetMapping("/preparation.csv")
    public ResponseEntity<String> csv(@PathVariable UUID invoiceId) {
        String content = documents.exportCsv(invoiceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("tax-preparation-" + invoiceId + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }

    @PutMapping("/external-record")
    public ApiResponse<ExternalRecord> record(@PathVariable UUID invoiceId, @RequestBody @Valid ExternalEvidence request) {
        return ApiResponse.success(documents.record(invoiceId, request));
    }
}
