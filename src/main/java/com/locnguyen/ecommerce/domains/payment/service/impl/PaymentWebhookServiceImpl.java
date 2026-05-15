package com.locnguyen.ecommerce.domains.payment.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.dto.WebhookLogResponse;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentTransaction;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentWebhookLog;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import com.locnguyen.ecommerce.domains.payment.enums.WebhookLogStatus;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentMapper;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentWebhookLogMapper;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderRegistry;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentWebhookLogRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final PaymentWebhookLogRepository webhookLogRepository;
    private final PaymentWebhookLogMapper webhookLogMapper;
    private final PaymentWebhookLogPersister webhookLogPersister;
    private final PaymentProviderRegistry providerRegistry;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentMapper paymentMapper;

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Entry point for all inbound gateway webhooks.
     *
     * <p>The initial log is committed in its own transaction via {@link PaymentWebhookLogPersister}
     * so that a trace exists even if subsequent processing rolls back.
     */
    @Override
    @Transactional
    public PaymentResponse receiveWebhook(String provider, String rawBody, String signature) {
        // Committed immediately in its own transaction — survives outer rollback
        PaymentWebhookLog webhookLog = webhookLogPersister.createInitialLog(provider, rawBody, signature);

        try {
            return processWebhook(webhookLog, provider, rawBody, signature);
        } catch (AppException e) {
            updateLog(webhookLog, WebhookLogStatus.FAILED, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing webhook: provider={} error={}", provider, e.getMessage(), e);
            updateLog(webhookLog, WebhookLogStatus.FAILED, "Internal error: " + e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookLogResponse> getLogsForPayment(UUID paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return webhookLogMapper.toResponses(
                webhookLogRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookLogResponse> getLogsByProviderTxnId(String providerTxnId) {
        return webhookLogMapper.toResponses(
                webhookLogRepository.findByProviderTxnIdOrderByCreatedAtDesc(providerTxnId));
    }

    // ─── Internal processing ─────────────────────────────────────────────────

    private PaymentResponse processWebhook(PaymentWebhookLog webhookLog,
                                           String provider, String rawBody, String signature) {
        // Resolve the provider implementation from the registry
        Optional<PaymentProvider> providerOpt = providerRegistry.find(provider);
        if (providerOpt.isEmpty()) {
            log.warn("Webhook received for unregistered provider: {}", provider);
            updateLog(webhookLog, WebhookLogStatus.IGNORED,
                    "Provider not registered: " + provider);
            return null;
        }
        PaymentProvider paymentProvider = providerOpt.get();

        // Signature verification — must occur before any business mutation
        boolean signatureValid = paymentProvider.verifySignature(rawBody, signature);
        webhookLog.setSignatureValid(signatureValid);
        if (!signatureValid) {
            log.warn("Webhook signature verification failed: provider={}", provider);
            updateLog(webhookLog, WebhookLogStatus.FAILED, "Signature verification failed");
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }

        // Extract order and provider transaction identifiers from the payload
        String orderCode = paymentProvider.extractOrderCode(rawBody);
        String providerTxnId = paymentProvider.extractProviderTxnId(rawBody);
        webhookLog.setOrderCode(orderCode);
        webhookLog.setProviderTxnId(providerTxnId);

        // Idempotency guard on providerTxnId — duplicate webhooks are logged then ignored
        if (providerTxnId != null) {
            Optional<PaymentTransaction> existingTxn = transactionRepository.findByProviderTxnId(providerTxnId);
            if (existingTxn.isPresent()) {
                log.warn("Duplicate webhook ignored — providerTxnId already processed: {}", providerTxnId);
                updateLog(webhookLog, WebhookLogStatus.IGNORED,
                        "Duplicate providerTxnId: " + providerTxnId);
                return paymentMapper.toResponse(existingTxn.get().getPayment());
            }
        }

        // Null orderCode: provider cannot extract an order reference from this event type
        // (e.g., PayPal CHECKOUT.ORDER.APPROVED before capture). Log and skip.
        if (orderCode == null) {
            log.info("Webhook has no extractable orderCode: provider={} providerTxnId={} — ignoring",
                    provider, providerTxnId);
            updateLog(webhookLog, WebhookLogStatus.IGNORED, "No orderCode extractable from payload");
            return null;
        }

        // Find order and acquire a row-level lock on payment to prevent race conditions
        var order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_CALLBACK_INVALID,
                        "Order not found for orderCode: " + orderCode));

        Payment payment = paymentRepository.findByOrderIdWithLock(order.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // Amount guard: reject IPN whose amount differs from the stored payment amount
        BigDecimal ipnAmount = paymentProvider.extractAmount(rawBody);
        if (ipnAmount != null && payment.getAmount().compareTo(ipnAmount) != 0) {
            log.warn("Webhook amount mismatch: provider={} paymentCode={} expected={} received={}",
                    provider, payment.getPaymentCode(), payment.getAmount(), ipnAmount);
            updateLog(webhookLog, WebhookLogStatus.FAILED, "Amount mismatch in IPN");
            throw new AppException(ErrorCode.PAYMENT_CALLBACK_INVALID,
                    "IPN amount does not match stored payment amount");
        }

        // Guard: already PAID → idempotent return
        if (payment.getStatus() == PaymentRecordStatus.PAID) {
            log.warn("Webhook ignored — payment already PAID: code={}", payment.getPaymentCode());
            updateLog(webhookLog, WebhookLogStatus.IGNORED, "Payment already PAID");
            return paymentMapper.toResponse(payment);
        }

        // Guard: refunded payments must not be overwritten
        if (payment.getStatus() == PaymentRecordStatus.REFUNDED
                || payment.getStatus() == PaymentRecordStatus.PARTIALLY_REFUNDED) {
            log.warn("Webhook ignored — payment has been refunded: code={}", payment.getPaymentCode());
            updateLog(webhookLog, WebhookLogStatus.IGNORED, "Payment already REFUNDED");
            return paymentMapper.toResponse(payment);
        }

        // Determine outcome and mutate payment + order status
        boolean success = paymentProvider.isSuccess(rawBody);

        if (success) {
            payment.setStatus(PaymentRecordStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
        } else {
            payment.setStatus(PaymentRecordStatus.FAILED);
            order.setPaymentStatus(PaymentStatus.FAILED);
        }
        payment = paymentRepository.save(payment);
        orderRepository.save(order);

        // Append immutable transaction record to the payment audit trail
        PaymentTransaction txn = new PaymentTransaction();
        txn.setPayment(payment);
        txn.setTransactionCode(CodeGenerator.generatePaymentTransactionCode());
        txn.setStatus(success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        txn.setAmount(payment.getAmount());
        txn.setMethod(payment.getMethod());
        txn.setProvider(provider);
        txn.setProviderTxnId(providerTxnId);
        txn.setReferenceType("WEBHOOK");
        txn.setReferenceId(orderCode);
        txn.setPayload(rawBody);
        txn.setNote(success ? "Payment confirmed via webhook" : "Payment rejected via webhook");
        transactionRepository.save(txn);

        // Link the log to the payment and mark it PROCESSED
        webhookLog.setPayment(payment);
        webhookLog.setProcessedAt(LocalDateTime.now());
        updateLog(webhookLog, WebhookLogStatus.PROCESSED, null);

        log.info("Webhook processed: provider={} orderCode={} paymentCode={} success={}",
                provider, orderCode, payment.getPaymentCode(), success);

        return paymentMapper.toResponse(payment);
    }

    private void updateLog(PaymentWebhookLog webhookLog, WebhookLogStatus status, String errorMessage) {
        webhookLog.setStatus(status);
        if (errorMessage != null) {
            webhookLog.setErrorMessage(errorMessage);
        }
        webhookLogRepository.save(webhookLog);
    }
}
