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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public RefundResponse initiateRefund(UUID paymentId, RefundRequest request) {
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

        // Idempotent: already completed
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            log.warn("Refund already COMPLETED, returning existing: refundCode={}", refundCode);
            return refundMapper.toResponse(refund);
        }

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_INVALID_STATUS,
                    "Refund can only be completed from PENDING status, current: " + refund.getStatus());
        }

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
