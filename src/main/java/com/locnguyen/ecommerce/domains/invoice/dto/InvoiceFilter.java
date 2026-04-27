package com.locnguyen.ecommerce.domains.invoice.dto;

import com.locnguyen.ecommerce.domains.invoice.enums.InvoiceStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class InvoiceFilter {
    private String invoiceCode;
    private String orderCode;
    private InvoiceStatus status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
