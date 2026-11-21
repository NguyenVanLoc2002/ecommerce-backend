package com.locnguyen.ecommerce.domains.invoice.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceFilter;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceResponse;
import com.locnguyen.ecommerce.domains.invoice.dto.UpdateInvoiceStatusRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface InvoiceService {

    InvoiceResponse generateInvoice(UUID orderId);

    InvoiceResponse updateStatus(UUID invoiceId, UpdateInvoiceStatusRequest request);

    InvoiceResponse getById(UUID invoiceId);

    InvoiceResponse getByOrderId(UUID orderId);

    InvoiceResponse getByCode(String invoiceCode);

    PagedResponse<InvoiceResponse> getInvoices(InvoiceFilter filter, Pageable pageable);

    InvoiceResponse getInvoiceForCustomer(UUID orderId, Customer customer);

    void markPaidByOrderId(UUID orderId);

    void recordRefundByOrderId(UUID orderId, BigDecimal refundedAmount, BigDecimal paymentAmount);

    void voidByOrderId(UUID orderId, String reason);
}
