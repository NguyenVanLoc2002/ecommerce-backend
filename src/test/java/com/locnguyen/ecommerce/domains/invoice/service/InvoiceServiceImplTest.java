package com.locnguyen.ecommerce.domains.invoice.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.domains.invoice.dto.UpdateInvoiceStatusRequest;
import com.locnguyen.ecommerce.domains.invoice.entity.Invoice;
import com.locnguyen.ecommerce.domains.invoice.enums.InvoiceStatus;
import com.locnguyen.ecommerce.domains.invoice.mapper.InvoiceMapper;
import com.locnguyen.ecommerce.domains.invoice.repository.InvoiceRepository;
import com.locnguyen.ecommerce.domains.invoice.service.impl.InvoiceServiceImpl;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock OrderRepository orderRepository;
    @Mock InvoiceMapper invoiceMapper;
    @InjectMocks InvoiceServiceImpl invoiceService;

    @Test
    void markPaidByOrderId_whenIssued_marksPaidAndIsIdempotent() {
        Invoice invoice = invoice(InvoiceStatus.ISSUED);
        when(invoiceRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(invoice));

        invoiceService.markPaidByOrderId(ORDER_ID);
        invoiceService.markPaidByOrderId(ORDER_ID);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        verify(invoiceRepository, times(1)).save(invoice);
    }

    @Test
    void recordRefundByOrderId_whenPartialThenFull_setsRefundStatusWithoutChangingSnapshot() {
        Invoice invoice = invoice(InvoiceStatus.ISSUED);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        when(invoiceRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(invoice));

        invoiceService.recordRefundByOrderId(ORDER_ID, new BigDecimal("25.00"), new BigDecimal("100.00"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_REFUNDED);

        invoiceService.recordRefundByOrderId(ORDER_ID, new BigDecimal("100.00"), new BigDecimal("100.00"));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.REFUNDED);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("100.00");
        verify(invoiceRepository, times(2)).save(invoice);
    }

    @Test
    void recordRefundByOrderId_whenVoided_keepsTerminalStatus() {
        Invoice invoice = invoice(InvoiceStatus.VOIDED);
        when(invoiceRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(invoice));

        invoiceService.recordRefundByOrderId(ORDER_ID, BigDecimal.TEN, new BigDecimal("100.00"));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VOIDED);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void voidByOrderId_whenIssued_appendsAuditReasonAndIsIdempotent() {
        Invoice invoice = invoice(InvoiceStatus.ISSUED);
        invoice.setNotes("Original note");
        when(invoiceRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(invoice));

        invoiceService.voidByOrderId(ORDER_ID, "Order cancelled by customer");
        invoiceService.voidByOrderId(ORDER_ID, "Duplicate cancellation");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VOIDED);
        assertThat(invoice.getNotes()).isEqualTo("Original note\nOrder cancelled by customer");
        verify(invoiceRepository, times(1)).save(invoice);
    }

    @Test
    void updateStatus_whenAskedToMarkPaidManually_rejects() {
        Invoice invoice = invoice(InvoiceStatus.ISSUED);
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        UpdateInvoiceStatusRequest request = new UpdateInvoiceStatusRequest();
        request.setStatus(InvoiceStatus.PAID);

        assertThatThrownBy(() -> invoiceService.updateStatus(INVOICE_ID, request))
                .isInstanceOf(AppException.class);
        verify(invoiceRepository, never()).save(any());
    }

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();

    private Invoice invoice(InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setStatus(status);
        return invoice;
    }
}
