package com.locnguyen.ecommerce.domains.invoice.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceFilter;
import com.locnguyen.ecommerce.domains.invoice.dto.InvoiceResponse;
import com.locnguyen.ecommerce.domains.invoice.dto.UpdateInvoiceStatusRequest;
import com.locnguyen.ecommerce.domains.invoice.entity.Invoice;
import com.locnguyen.ecommerce.domains.invoice.enums.InvoiceStatus;
import com.locnguyen.ecommerce.domains.invoice.mapper.InvoiceMapper;
import com.locnguyen.ecommerce.domains.invoice.repository.InvoiceRepository;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import com.locnguyen.ecommerce.domains.invoice.specification.InvoiceSpecification;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    /** Statuses that are eligible for invoice generation. */
    private static final Set<OrderStatus> INVOICEABLE_STATUSES = Set.of(
            OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final InvoiceMapper invoiceMapper;

    // ─── Admin operations ─────────────────────────────────────────────────────

    /**
     * Generate (or retrieve existing) invoice for an order.
     *
     * <p>Idempotent: calling again for an order that already has an invoice returns the
     * existing record without modification.
     *
     * <p>Business rule: the order must be in CONFIRMED or a later active status.
     * PENDING and CANCELLED orders cannot be invoiced.
     */
    @Transactional
    public InvoiceResponse generateInvoice(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // Idempotent: return existing
        if (invoiceRepository.existsByOrderId(orderId)) {
            log.info("Invoice already exists for order {}", order.getOrderCode());
            return invoiceMapper.toResponse(findByOrderIdOrThrow(orderId));
        }

        if (!INVOICEABLE_STATUSES.contains(order.getStatus())) {
            throw new AppException(ErrorCode.INVOICE_STATUS_INVALID,
                    "Cannot generate invoice for order in status: " + order.getStatus());
        }

        User user = order.getCustomer().getUser();
        String customerName = buildFullName(user.getFirstName(), user.getLastName());

        if (order.getReplacementForOrderId() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Replacement orders do not have a sales invoice");
        }

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setInvoiceCode(CodeGenerator.generateInvoiceCode());
        invoice.setStatus(statusForPayment(order.getPaymentStatus()));
        invoice.setIssuedAt(LocalDateTime.now());

        // Customer snapshot
        invoice.setCustomerName(customerName);
        invoice.setCustomerEmail(user.getEmail());
        invoice.setCustomerPhone(user.getPhoneNumber());

        // Billing address — use the order's shipping address snapshot
        invoice.setBillingStreet(order.getShippingStreet());
        invoice.setBillingWard(order.getShippingWard());
        invoice.setBillingDistrict(order.getShippingDistrict());
        invoice.setBillingCity(order.getShippingCity());
        invoice.setBillingPostalCode(order.getShippingPostalCode());

        // Amounts snapshot
        invoice.setSubTotal(order.getSubTotal());
        invoice.setDiscountAmount(order.getDiscountAmount());
        invoice.setShippingFee(order.getShippingFee());
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setVoucherCode(order.getVoucherCode());

        invoice = invoiceRepository.save(invoice);

        log.info("Invoice generated: code={} orderId={}", invoice.getInvoiceCode(), orderId);
        return invoiceMapper.toResponse(findByIdOrThrow(invoice.getId()));
    }

    /**
     * Update invoice status.
     *
     * <p>Allowed transitions:
     * <ul>
     *   <li>ISSUED → PAID (payment confirmed externally)</li>
     *   <li>ISSUED → VOIDED (cancellation, error correction)</li>
     * </ul>
     * PAID and VOIDED are terminal — no further updates allowed.
     */
    @Transactional
    public InvoiceResponse updateStatus(UUID invoiceId, UpdateInvoiceStatusRequest request) {
        Invoice invoice = findByIdOrThrow(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new AppException(ErrorCode.INVOICE_STATUS_INVALID,
                    "Invoice is in terminal status: " + invoice.getStatus());
        }

        InvoiceStatus requested = request.getStatus();
        if (requested != InvoiceStatus.VOIDED) {
            throw new AppException(ErrorCode.INVOICE_STATUS_INVALID,
                    "Invoice can only be voided manually; payment statuses are synchronized from confirmed payment events");
        }
        if (request.getNotes() == null || request.getNotes().isBlank()) {
            throw new AppException(ErrorCode.INVOICE_STATUS_INVALID,
                    "A reason is required when voiding an invoice");
        }

        invoice.setStatus(requested);
        if (request.getNotes() != null) {
            invoice.setNotes(request.getNotes());
        }

        invoiceRepository.save(invoice);
        log.info("Invoice status updated: id={} → {}", invoiceId, requested);
        return invoiceMapper.toResponse(findByIdOrThrow(invoiceId));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(UUID invoiceId) {
        return invoiceMapper.toResponse(findByIdOrThrow(invoiceId));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getByOrderId(UUID orderId) {
        return invoiceMapper.toResponse(findByOrderIdOrThrow(orderId));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getByCode(String invoiceCode) {
        Invoice invoice = invoiceRepository.findByInvoiceCode(invoiceCode)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
        return invoiceMapper.toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InvoiceResponse> getInvoices(InvoiceFilter filter, Pageable pageable) {
        Page<Invoice> page = invoiceRepository.findAll(
                InvoiceSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(invoiceMapper::toListItemResponse));
    }

    // ─── Customer operations ──────────────────────────────────────────────────

    /**
     * Get the invoice for a customer's own order.
     * Enforces ownership — throws {@code ORDER_NOT_FOUND} if the order belongs to another customer.
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceForCustomer(UUID orderId, Customer customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        return invoiceMapper.toResponse(findByOrderIdOrThrow(orderId));
    }

    // ─── Internal: auto-mark invoice as PAID on payment confirmation ──────────

    /**
     * Marks an invoice PAID when the linked order's payment is confirmed.
     * Called internally by {@code PaymentService} or event handlers.
     * No-op if the invoice does not exist or is already terminal.
     */
    @Transactional
    public void markPaidByOrderId(UUID orderId) {
        invoiceRepository.findByOrderId(orderId).ifPresent(invoice -> {
            if (invoice.getStatus() == InvoiceStatus.ISSUED) {
                invoice.setStatus(InvoiceStatus.PAID);
                invoiceRepository.save(invoice);
                log.info("Invoice auto-marked as PAID: code={}", invoice.getInvoiceCode());
            }
        });
    }

    @Override
    @Transactional
    public void recordRefundByOrderId(UUID orderId, BigDecimal refundedAmount, BigDecimal paymentAmount) {
        invoiceRepository.findByOrderId(orderId).ifPresent(invoice -> {
            if (invoice.getStatus() == InvoiceStatus.VOIDED) {
                return;
            }
            InvoiceStatus refundedStatus = refundedAmount.compareTo(paymentAmount) >= 0
                    ? InvoiceStatus.REFUNDED : InvoiceStatus.PARTIALLY_REFUNDED;
            if (invoice.getStatus() != refundedStatus) {
                invoice.setStatus(refundedStatus);
                invoiceRepository.save(invoice);
            }
        });
    }

    @Override
    @Transactional
    public void voidByOrderId(UUID orderId, String reason) {
        invoiceRepository.findByOrderId(orderId).ifPresent(invoice -> {
            if (invoice.getStatus() == InvoiceStatus.ISSUED) {
                invoice.setStatus(InvoiceStatus.VOIDED);
                invoice.setNotes(appendNote(invoice.getNotes(), reason));
                invoiceRepository.save(invoice);
            }
        });
    }

    private InvoiceStatus statusForPayment(PaymentStatus status) {
        if (status == null) return InvoiceStatus.ISSUED;
        return switch (status) {
            case PAID -> InvoiceStatus.PAID;
            case PARTIALLY_REFUNDED -> InvoiceStatus.PARTIALLY_REFUNDED;
            case REFUNDED -> InvoiceStatus.REFUNDED;
            default -> InvoiceStatus.ISSUED;
        };
    }

    private String appendNote(String current, String addition) {
        if (current == null || current.isBlank()) return addition;
        if (current.length() + addition.length() + 1 > 1000) return current;
        return current + "\n" + addition;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Invoice findByIdOrThrow(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
    }

    private Invoice findByOrderIdOrThrow(UUID orderId) {
        return invoiceRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) return "";
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }
}
