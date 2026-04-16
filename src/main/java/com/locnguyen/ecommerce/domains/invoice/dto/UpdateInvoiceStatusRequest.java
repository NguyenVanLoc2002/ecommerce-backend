package com.locnguyen.ecommerce.domains.invoice.dto;

import com.locnguyen.ecommerce.domains.invoice.enums.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to update an invoice status")
public class UpdateInvoiceStatusRequest {

    @NotNull
    @Schema(description = "New status: PAID or VOIDED")
    private InvoiceStatus status;

    @Size(max = 1000)
    private String notes;
}
