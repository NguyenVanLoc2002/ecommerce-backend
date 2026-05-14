package com.locnguyen.ecommerce.domains.payment.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
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

    @Override
    @Transactional
    public RefundResponse initiateRefund(UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentRecordStatus.PAID
                && payment.getStatus() != PaymentRecordStatus.PARTIALLY_REFUNDED) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_INVALID_STATUS,
                    "Payment must be PAID or PARTIALLY_REFUNDED to initiate a refund, current status: "
                            + payment.getStatus());
        }

        // Sum existing COMPLETED refunds to guard against over-refunding
        List<PaymentRefund> existingRefunds = refundRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
        BigDecimal alreadyRefunded = existingRefunds.stream()
                .filter(r -> r.getStatus() == RefundStatus.COMPLETED)
                .map(PaymentRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (alreadyRefunded.add(request.amount()).compareTo(payment.getAmount()) > 0) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_AMOUNT_EXCEEDED,
                    "Refund of " + request.amount() + " would exceed the remaining refundable amount of "
                            + payment.getAmount().subtract(alreadyRefunded));
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

        // Update payment status based on whether this refund covers the full amount
        BigDecimal totalRefunded = alreadyRefunded.add(request.amount());
        if (totalRefunded.compareTo(payment.getAmount()) == 0) {
            payment.setStatus(PaymentRecordStatus.REFUNDED);
            payment.getOrder().setPaymentStatus(PaymentStatus.REFUNDED);
            orderRepository.save(payment.getOrder());
        } else {
            payment.setStatus(PaymentRecordStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        // Record as an immutable transaction entry
        PaymentTransaction txn = new PaymentTransaction();
        txn.setPayment(payment);
        txn.setTransactionCode(CodeGenerator.generatePaymentTransactionCode());
        txn.setStatus(TransactionStatus.REFUNDED);
        txn.setAmount(request.amount());
        txn.setMethod(payment.getMethod());
        txn.setReferenceType("REFUND");
        txn.setReferenceId(refund.getRefundCode());
        txn.setNote("Refund initiated: " + refund.getRefundCode());
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
