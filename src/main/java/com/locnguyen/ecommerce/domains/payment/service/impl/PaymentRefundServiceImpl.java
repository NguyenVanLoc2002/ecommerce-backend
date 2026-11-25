package com.locnguyen.ecommerce.domains.payment.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.dto.RefundRequest;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentRefund;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentTransaction;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentRefundMapper;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRefundRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentProviderType;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.PaypalClient;
import java.time.LocalDateTime;
import com.locnguyen.ecommerce.domains.payment.dto.ReturnRefundAllocation;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRefundServiceImpl implements PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final PaymentRefundMapper refundMapper;
    private final EntityManager entityManager;
    private final InvoiceService invoiceService;
    private final PaypalClient paypalClient;

    @Override
    @Transactional
    public RefundResponse initiateRefund(UUID paymentId, RefundRequest request) {
        return initiateRefund(paymentId, request, null, null, null);
    }

    @Override
    public Optional<RefundResponse> findReturnRefund(UUID returnId) {
        return refundRepository.findByReturnId(returnId).map(refundMapper::toResponse);
    }

    @Override
    @Transactional
    public List<ReturnRefundAllocation> getReservedReturnAllocations(UUID orderId) {
        var payment = paymentRepository.findByOrderIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        var reserved = refundRepository.findByPaymentIdAndStatusIn(payment.getId(),
                List.of(RefundStatus.PENDING, RefundStatus.PROCESSING, RefundStatus.COMPLETED));
        if (reserved.stream().anyMatch(refund -> refund.getReturnId() == null || refund.getReturnLineAllocations() == null)) {
            throw new AppException(ErrorCode.RETURN_REFUND_REVIEW_REQUIRED);
        }
        return reserved.stream().flatMap(refund -> refund.getReturnLineAllocations().stream()).toList();
    }

    @Override
    @Transactional
    public RefundResponse initiateReturnRefund(UUID orderId, UUID returnId,
                                                List<ReturnRefundAllocation> lines, BigDecimal shipping) {
        BigDecimal amount = lines.stream().map(ReturnRefundAllocation::amount).reduce(shipping, BigDecimal::add);
        if (amount.signum() <= 0) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_INVALID_STATUS, "No refundable balance on the returned lines");
        }
        UUID paymentId = paymentRepository.findByOrderId(orderId).map(Payment::getId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        return initiateRefund(paymentId, new RefundRequest(amount, "Return settlement", null), returnId, lines, shipping);
    }

    private RefundResponse initiateRefund(UUID paymentId, RefundRequest request, UUID returnId,
                                          List<ReturnRefundAllocation> lines, BigDecimal shipping) {
        UUID orderId = paymentRepository.findOrderIdByPaymentId(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        Order lockedOrder = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.setOrder(lockedOrder);

        if (payment.getStatus() != PaymentRecordStatus.PAID
                && payment.getStatus() != PaymentRecordStatus.PARTIALLY_REFUNDED) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_INVALID_STATUS,
                    "Payment must be PAID or PARTIALLY_REFUNDED to initiate a refund, current status: "
                            + payment.getStatus());
        }

        // Pending and processing refunds reserve their amount against concurrent requests.
        List<PaymentRefund> reservingRefunds = refundRepository.findByPaymentIdAndStatusIn(
                paymentId, List.of(RefundStatus.COMPLETED, RefundStatus.PENDING, RefundStatus.PROCESSING));
        BigDecimal reservedAmount = reservingRefunds.stream()
                .map(PaymentRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (reservedAmount.add(request.amount()).compareTo(payment.getAmount()) > 0) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_AMOUNT_EXCEEDED,
                    "Refund of " + request.amount() + " would exceed the remaining refundable amount of "
                            + payment.getAmount().subtract(reservedAmount));
        }

        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment);
        refund.setRefundCode(CodeGenerator.generateRefundCode());
        refund.setAmount(request.amount());
        refund.setReturnId(returnId);
        refund.setReturnLineAllocations(lines);
        refund.setReturnShippingAmount(shipping);
        refund.setReason(request.reason());
        refund.setNote(request.note());
        refund.setStatus(RefundStatus.PENDING);
        refund.setRequestedBy(resolveRequestedBy());
        refund = refundRepository.save(refund);

        // Initiation is only a request; record it as pending without changing settled payment state.
        PaymentTransaction txn = new PaymentTransaction();
        txn.setPayment(payment);
        txn.setTransactionCode(CodeGenerator.generatePaymentTransactionCode());
        txn.setStatus(TransactionStatus.INITIATED);
        txn.setAmount(request.amount());
        txn.setMethod(payment.getMethod());
        txn.setReferenceType("REFUND");
        txn.setReferenceId(refund.getRefundCode());
        txn.setNote("Refund requested: " + refund.getRefundCode());
        transactionRepository.save(txn);

        log.info("Refund initiated: refundCode={} paymentCode={} amount={} requestedBy={}",
                refund.getRefundCode(), payment.getPaymentCode(), request.amount(), refund.getRequestedBy());

        return refundMapper.toResponse(refund);
    }

    @Override
    @Transactional
    public RefundResponse submitCodReturnRefundEvidence(UUID orderId, UUID returnId, String reference, String actor) {
        var refund = lockReturnRefund(orderId, returnId);
        requireMethod(refund, PaymentMethod.COD);
        String key = "COD:" + reference;
        if (key.equals(refund.getSettlementReference()) && actor.equals(refund.getSettlementSubmittedBy())) {
            return refundMapper.toResponse(refund);
        }
        if (refund.getStatus() != RefundStatus.PENDING) throw invalidSettlement();
        refund.setSettlementReference(key);
        refund.setSettlementSubmittedBy(actor);
        refund.setStatus(RefundStatus.PROCESSING);
        return refundMapper.toResponse(refundRepository.saveAndFlush(refund));
    }

    @Override
    @Transactional
    public RefundResponse confirmCodReturnRefund(UUID orderId, UUID returnId, String actor) {
        var refund = lockReturnRefund(orderId, returnId);
        requireMethod(refund, PaymentMethod.COD);
        String maker = refund.getSettlementSubmittedBy();
        if (maker == null || maker.equalsIgnoreCase(actor) || refund.getSettlementReference() == null) {
            throw invalidSettlement();
        }
        if (refund.getStatus() == RefundStatus.COMPLETED) return refundMapper.toResponse(refund);
        if (refund.getStatus() != RefundStatus.PROCESSING) throw invalidSettlement();
        return finalizeRefund(refund, refund.getPayment(), refund.getPayment().getOrder(), orderId,
                refund.getSettlementReference().substring("COD:".length()));
    }

    @Override
    @Transactional
    public RefundResponse verifyPaypalReturnRefund(UUID orderId, UUID returnId, String reference) {
        var refund = lockReturnRefund(orderId, returnId);
        requireMethod(refund, PaymentMethod.ONLINE);
        if (refund.getStatus() == RefundStatus.COMPLETED && reference.equals(refund.getProviderRefundId())) {
            return refundMapper.toResponse(refund);
        }
        if (refund.getStatus() != RefundStatus.PENDING || !reference.matches("[A-Za-z0-9-]{1,100}")) {
            throw invalidSettlement();
        }
        Payment payment = refund.getPayment();
        BigDecimal rate = payment.getProviderExchangeRate();
        if (rate == null || rate.signum() <= 0 || payment.getProviderCurrency() == null) {
            throw new AppException(ErrorCode.RETURN_REFUND_REVIEW_REQUIRED);
        }
        var captureIds = transactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()).stream()
                .filter(txn -> txn.getProvider() == PaymentProviderType.PAYPAL && txn.getStatus() == TransactionStatus.SUCCESS
                        && ("CAPTURE".equals(txn.getReferenceType()) || "LATE_SUCCESS_AFTER_CANCEL".equals(txn.getReferenceType())))
                .map(PaymentTransaction::getProviderTxnId)
                .filter(id -> id != null && id.matches("[A-Za-z0-9-]+"))
                .distinct().toList();
        if (captureIds.size() != 1) throw new AppException(ErrorCode.RETURN_REFUND_REVIEW_REQUIRED);

        var verified = paypalClient.getRefund(reference);
        var expectedAmount = refund.getAmount().divide(rate, 2, RoundingMode.HALF_UP);
        String capturePath = "/v2/payments/captures/" + captureIds.get(0);
        if (!reference.equals(verified.id()) || !refund.getRefundCode().equals(verified.invoiceId())
                || verified.amount() == null || verified.amount().value() == null
                || expectedAmount.signum() <= 0 || expectedAmount.compareTo(verified.amount().value()) != 0
                || !payment.getProviderCurrency().equals(verified.amount().currency())
                || verified.links() == null || verified.links().stream().noneMatch(link ->
                        "up".equals(link.getRel()) && link.getHref() != null && link.getHref().endsWith(capturePath))) {
            throw invalidSettlement();
        }
        if (!"COMPLETED".equals(verified.status())) return refundMapper.toResponse(refund);

        requireUnusedProviderReference(payment.getId(), reference);
        refund.setSettlementReference("PAYPAL:" + reference);
        return finalizeRefund(refund, payment, payment.getOrder(), orderId, reference);
    }

    private void requireUnusedProviderReference(UUID paymentId, String reference) {
        if (reference != null && refundRepository.findByPaymentIdAndStatusIn(paymentId, List.of(RefundStatus.COMPLETED))
                .stream().anyMatch(row -> reference.equals(row.getProviderRefundId()))) {
            throw new AppException(ErrorCode.CONFLICT, "This refund reference was already settled");
        }
    }

    private PaymentRefund lockReturnRefund(UUID orderId, UUID returnId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        var refund = refundRepository.findByReturnId(returnId)
                .filter(row -> payment.getId().equals(row.getPayment().getId()))
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_REFUND_NOT_FOUND));
        entityManager.refresh(refund, LockModeType.PESSIMISTIC_WRITE);
        refund.setPayment(payment);
        payment.setOrder(order);
        return refund;
    }

    private void requireMethod(PaymentRefund refund, PaymentMethod expected) {
        if (refund.getPayment().getMethod() != expected) throw invalidSettlement();
    }

    private AppException invalidSettlement() {
        return new AppException(ErrorCode.PAYMENT_REFUND_INVALID_STATUS, "Return refund settlement evidence is invalid or conflicts with its current state");
    }

    @Override
    @Transactional
    public RefundResponse completeRefund(String refundCode, String providerRefundId) {
        PaymentRefund refund = refundRepository.findByRefundCode(refundCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_REFUND_NOT_FOUND));

        UUID paymentId = refund.getPayment().getId();
        UUID orderId = paymentRepository.findOrderIdByPaymentId(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        Order lockedOrder = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.setOrder(lockedOrder);
        entityManager.refresh(refund, LockModeType.PESSIMISTIC_WRITE);
        refund = refundRepository.findByRefundCode(refundCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_REFUND_NOT_FOUND));

        if (refund.getReturnId() != null) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_INVALID_STATUS,
                    "Return refunds require verified settlement");
        }

        // Idempotent: already completed
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            log.warn("Refund already COMPLETED, returning existing: refundCode={}", refundCode);
            return refundMapper.toResponse(refund);
        }

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_INVALID_STATUS,
                    "Refund can only be completed from PENDING status, current: " + refund.getStatus());
        }

        return finalizeRefund(refund, payment, lockedOrder, orderId, providerRefundId);
    }

    private RefundResponse finalizeRefund(PaymentRefund refund, Payment payment, Order lockedOrder,
                                          UUID orderId, String providerRefundId) {
        String refundCode = refund.getRefundCode();
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setProviderRefundId(providerRefundId);
        refund.setRefundedAt(LocalDateTime.now());
        refund = refundRepository.save(refund);

        List<PaymentRefund> completedRefunds = refundRepository.findByPaymentIdAndStatusIn(
                payment.getId(), List.of(RefundStatus.COMPLETED));
        BigDecimal completedAmount = completedRefunds.stream()
                .map(PaymentRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (completedAmount.compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentRecordStatus.REFUNDED);
            lockedOrder.setPaymentStatus(PaymentStatus.REFUNDED);
        } else if (completedAmount.signum() > 0) {
            payment.setStatus(PaymentRecordStatus.PARTIALLY_REFUNDED);
            lockedOrder.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        } else {
            payment.setStatus(PaymentRecordStatus.PAID);
            lockedOrder.setPaymentStatus(PaymentStatus.PAID);
        }
        orderRepository.save(lockedOrder);
        paymentRepository.save(payment);
        invoiceService.recordRefundByOrderId(orderId, completedAmount, payment.getAmount());

        PaymentTransaction txn = new PaymentTransaction();
        txn.setPayment(payment);
        txn.setTransactionCode(CodeGenerator.generatePaymentTransactionCode());
        txn.setStatus(TransactionStatus.REFUNDED);
        txn.setAmount(refund.getAmount());
        txn.setMethod(payment.getMethod());
        txn.setReferenceType("REFUND");
        txn.setReferenceId(refund.getRefundCode());
        txn.setProviderTxnId(providerRefundId);
        txn.setNote("Refund confirmed: " + refund.getRefundCode());
        transactionRepository.save(txn);

        log.info("Refund completed: refundCode={} providerRefundId={}", refundCode, providerRefundId);
        return refundMapper.toResponse(refund);
    }

    @Override
    public List<RefundResponse> getRefundsForPayment(UUID paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return refundMapper.toResponses(
                refundRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId));
    }

    @Override
    public RefundResponse getRefundByCode(String refundCode) {
        return refundMapper.toResponse(
                refundRepository.findByRefundCode(refundCode)
                        .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_REFUND_NOT_FOUND)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String resolveRequestedBy() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // Non-fatal: SecurityContext unavailable in async/scheduled context
        }
        return "system";
    }
}
