package com.locnguyen.ecommerce.domains.payment.service.impl;

import com.locnguyen.ecommerce.common.config.payment.PaymentProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.common.utils.RequestHashUtils;
import com.locnguyen.ecommerce.domains.idempotency.entity.IdempotencyKey;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyActionType;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyStatus;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.dto.*;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentTransaction;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentProviderType;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentMapper;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCaptureResult;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCreateResult;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderRegistry;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.payment.specification.PaymentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final IdempotencyService idempotencyService;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentProperties paymentProperties;

    // ─── COD payment ────────────────────────────────────────────────────────

    /**
     * Create a COD payment record immediately on order creation.
     * Called exclusively by {@code OrderService} after a COD order is persisted.
     *
     * <p>Idempotent: returns the existing record if one already exists for the order.
     */
    @Transactional
    public PaymentResponse createCodPayment(Order order) {
        if (paymentRepository.existsByOrderId(order.getId())) {
            log.warn("Payment already exists for order: code={}", order.getOrderCode());
            return paymentMapper.toResponse(findByOrderIdOrThrow(order.getId()));
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentCode(CodeGenerator.generatePaymentCode());
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentRecordStatus.PENDING);
        payment.setAmount(order.getTotalAmount());

        payment = paymentRepository.save(payment);

        recordTransaction(payment, TransactionStatus.INITIATED,
                PaymentMethod.COD, null, null,
                "COD payment created for order " + order.getOrderCode());

        log.info("COD payment created: code={} orderCode={} amount={}",
                payment.getPaymentCode(), order.getOrderCode(), order.getTotalAmount());
        return paymentMapper.toResponse(payment);
    }

    /**
     * Mark a COD payment as paid — admin calls this on delivery confirmation.
     *
     * <p>Idempotent: if the payment is already PAID, returns the existing record silently.
     * Throws {@code PAYMENT_ALREADY_PROCESSED} for non-PENDING statuses (e.g., REFUNDED).
     */
    @Transactional
    public PaymentResponse completeCodPayment(UUID orderId) {
        Payment payment = findByOrderIdOrThrow(orderId);

        // Idempotent: already paid → return silently
        if (payment.getStatus() == PaymentRecordStatus.PAID) {
            log.warn("COD payment already marked as paid: code={}", payment.getPaymentCode());
            return paymentMapper.toResponse(payment);
        }

        if (payment.getStatus() != PaymentRecordStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "COD payment cannot be completed in status: " + payment.getStatus());
        }

        payment.setStatus(PaymentRecordStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        // Sync denormalized payment status on Order
        Order order = payment.getOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        recordTransaction(payment, TransactionStatus.SUCCESS,
                PaymentMethod.COD, null, null,
                "COD payment collected for order " + order.getOrderCode());

        log.info("COD payment completed: code={} orderCode={}",
                payment.getPaymentCode(), order.getOrderCode());
        return paymentMapper.toResponse(payment);
    }

    // ─── Online payment ──────────────────────────────────────────────────────

    /**
     * Initiate an online payment for a customer's order.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Customer must own the order — throws ORDER_NOT_FOUND otherwise</li>
     *   <li>Order payment method must be ONLINE</li>
     *   <li>INITIATED/PENDING → idempotent return (in-flight, don't duplicate)</li>
     *   <li>FAILED → allow retry: reset to INITIATED, record new transaction</li>
     *   <li>PAID / REFUNDED → throw PAYMENT_ALREADY_PROCESSED</li>
     * </ul>
     *
     * <p>Idempotency: same Idempotency-Key + same payload returns the existing payment
     * without creating a duplicate record or calling the external provider again.
     */
    @Transactional
    public PaymentResponse initiateOnlinePayment(UUID orderId, Customer customer,
                                                 InitPaymentRequest request, String idempotencyKey) {
        // ── Idempotency gate ───────────────────────────────────────────────────
        String requestHash = RequestHashUtils.hashFields(
                customer.getId(),
                "orderId", orderId.toString(),
                "provider", request.getProvider() != null ? request.getProvider() : ""
        );
        IdempotencyKey idem = idempotencyService.findOrCreateProcessing(
                customer.getId(), IdempotencyActionType.PAYMENT_INITIATE, idempotencyKey, requestHash);

        if (idem.getStatus() == IdempotencyStatus.COMPLETED) {
            UUID existingPaymentId = UUID.fromString(idem.getResourceId());
            Payment existing = paymentRepository.findById(existingPaymentId)
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
            return paymentMapper.toResponse(existing);
        }

        try {
            return executeInitiateOnlinePayment(orderId, customer, request, idem);
        } catch (AppException e) {
            idempotencyService.markFailed(idem.getId(), e.getErrorCode().getCode());
            throw e;
        } catch (Exception e) {
            idempotencyService.markFailed(idem.getId(), ErrorCode.INTERNAL_SERVER_ERROR.getCode());
            throw e;
        }
    }

    // ─── Capture online payment ───────────────────────────────────────────────

    /**
     * Captures an authorized online payment after the customer returns from the provider.
     *
     * <p>Called when the frontend receives the provider's return redirect (e.g., PayPal
     * {@code ?token=<paypalOrderId>}) and submits the token to the backend.
     *
     * <p>Idempotency guarantees:
     * <ul>
     *   <li>Payment already PAID → returns the existing record silently.</li>
     *   <li>Pessimistic write lock prevents concurrent captures from double-processing.</li>
     *   <li>Provider token is verified against the stored {@code providerOrderId} to prevent
     *       substitution attacks.</li>
     * </ul>
     */
    @Transactional
    public PaymentResponse captureOnlinePayment(UUID orderId, Customer customer,
                                                PaymentCaptureRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Order payment method is not ONLINE");
        }

        // Acquire row-level lock to serialize concurrent capture attempts
        Payment payment = paymentRepository.findByOrderIdWithLock(order.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // Idempotent: already PAID
        if (payment.getStatus() == PaymentRecordStatus.PAID) {
            log.info("Capture skipped — payment already PAID: code={}", payment.getPaymentCode());
            return paymentMapper.toResponse(payment);
        }

        if (payment.getStatus() != PaymentRecordStatus.INITIATED
                && payment.getStatus() != PaymentRecordStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "Payment in status " + payment.getStatus() + " cannot be captured");
        }

        // Verify the token matches what was stored during initiation to prevent substitution
        String providerToken = request.getProviderToken();
        if (payment.getProviderOrderId() == null
                || !payment.getProviderOrderId().equals(providerToken)) {
            log.warn("Capture token mismatch: orderId={} storedProviderOrderId={} requestToken={}",
                    orderId, payment.getProviderOrderId(), providerToken);
            throw new AppException(ErrorCode.PAYMENT_CALLBACK_INVALID,
                    "Provider token does not match payment record");
        }

        String providerName = request.getProvider().toUpperCase();
        PaymentProvider provider = providerRegistry.find(providerName)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_PROVIDER_NOT_SUPPORTED,
                        "Provider not registered: " + providerName));

        PaymentProviderCaptureResult captureResult = provider.capturePayment(payment, providerToken)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_FAILED,
                        "Provider " + providerName + " does not support capture"));

        boolean success = captureResult.isSuccess();

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

        PaymentTransaction txn = new PaymentTransaction();
        txn.setPayment(payment);
        txn.setTransactionCode(CodeGenerator.generatePaymentTransactionCode());
        txn.setStatus(success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        txn.setAmount(payment.getAmount());
        txn.setMethod(PaymentMethod.ONLINE);
        txn.setProvider(PaymentProviderType.fromValue(providerName));
        txn.setProviderTxnId(captureResult.getProviderTxnId());
        txn.setReferenceType("CAPTURE");
        txn.setReferenceId(order.getOrderCode());
        txn.setNote(success
                ? "Payment captured successfully"
                : "Payment capture failed: " + captureResult.getStatus());
        transactionRepository.save(txn);

        log.info("Payment capture processed: orderId={} orderCode={} paymentCode={} success={}",
                orderId, order.getOrderCode(), payment.getPaymentCode(), success);

        return paymentMapper.toResponse(payment);
    }

    private PaymentResponse executeInitiateOnlinePayment(UUID orderId, Customer customer,
                                                         InitPaymentRequest request,
                                                         IdempotencyKey idem) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // Ownership check — don't reveal the order exists to other customers
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Order payment method is not ONLINE");
        }

        Payment resultPayment;

        // Handle existing payment record (idempotency + retry)
        if (paymentRepository.existsByOrderId(orderId)) {
            Payment existing = findByOrderIdOrThrow(orderId);

            switch (existing.getStatus()) {
                case INITIATED, PENDING -> {
                    // Already in-flight — return existing record (idempotent)
                    log.info("Online payment already in-flight, returning existing: code={}",
                            existing.getPaymentCode());
                    resultPayment = existing;
                }
                case PAID, REFUNDED, PARTIALLY_REFUNDED ->
                        throw new AppException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                                "Payment is already in terminal status: " + existing.getStatus());
                case FAILED -> {
                    // Allow retry — reset to INITIATED and append a new transaction
                    existing.setStatus(PaymentRecordStatus.INITIATED);
                    existing.setExpiredAt(LocalDateTime.now().plusHours(24));
                    existing = paymentRepository.save(existing);

                    recordTransaction(existing, TransactionStatus.INITIATED, PaymentMethod.ONLINE,
                            request.getProvider(), null,
                            "Online payment re-initiated after failure for order " + order.getOrderCode());

                    log.info("Online payment re-initiated after FAILED: code={}",
                            existing.getPaymentCode());
                    resultPayment = existing;
                }
                default -> throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "Unhandled payment status: " + existing.getStatus());
            }
        } else {
            // No existing record — create new
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setPaymentCode(CodeGenerator.generatePaymentCode());
            payment.setMethod(PaymentMethod.ONLINE);
            payment.setStatus(PaymentRecordStatus.INITIATED);
            payment.setAmount(order.getTotalAmount());
            payment.setExpiredAt(LocalDateTime.now().plusHours(24));

            payment = paymentRepository.save(payment);

            // Reflect in the denormalized Order.paymentStatus
            order.setPaymentStatus(PaymentStatus.PENDING);
            orderRepository.save(order);

            recordTransaction(payment, TransactionStatus.INITIATED, PaymentMethod.ONLINE,
                    request.getProvider(), null,
                    "Online payment initiated for order " + order.getOrderCode());

            log.info("Online payment initiated: code={} orderCode={} provider={}",
                    payment.getPaymentCode(), order.getOrderCode(), request.getProvider());
            resultPayment = payment;
        }

        PaymentResponse response = paymentMapper.toResponse(resultPayment);

        // Resolve provider URL BEFORE marking idempotency complete so that a provider
        // failure marks the key as FAILED and allows the client to retry.
        PaymentProviderCreateResult providerResult = resolveProviderResult(
                resultPayment, order, request.getProvider(), request.getReturnUrl());

        if (providerResult != null) {
            if (providerResult.getProviderOrderId() != null) {
                resultPayment.setProviderOrderId(providerResult.getProviderOrderId());
                resultPayment.setProviderRequestId(providerResult.getProviderRequestId());
                paymentRepository.save(resultPayment);
            }
            PaymentResponse.PaymentResponseBuilder builder = response.toBuilder();
            if (providerResult.getPaymentUrl() != null) {
                builder.paymentUrl(providerResult.getPaymentUrl());
            }
            if (providerResult.getDeeplink() != null) {
                builder.deeplink(providerResult.getDeeplink());
            }
            if (providerResult.getQrCodeUrl() != null) {
                builder.qrCodeUrl(providerResult.getQrCodeUrl());
            }
            response = builder.build();
        }

        idempotencyService.markComplete(
                idem.getId(), "PAYMENT", resultPayment.getId().toString(), 201);

        return response;
    }

    private PaymentProviderCreateResult resolveProviderResult(Payment payment, Order order,
                                                              String providerName, String returnUrl) {
        if (providerName == null) return null;
        return providerRegistry.find(providerName)
                .map(provider -> {
                    String callbackUrl = paymentProperties.getBaseCallbackUrl();
                    String resolvedReturnUrl = (returnUrl != null && !returnUrl.isBlank())
                            ? returnUrl
                            : paymentProperties.getDefaultReturnUrl();
                    try {
                        return provider.createPayment(payment, order, resolvedReturnUrl, callbackUrl);
                    } catch (AppException e) {
                        throw e;
                    } catch (Exception e) {
                        log.error("Unexpected error creating payment with provider: provider={} orderCode={} — {}",
                                providerName, order.getOrderCode(), e.getMessage(), e);
                        throw new AppException(ErrorCode.PAYMENT_FAILED,
                                "Provider " + providerName + " encountered an unexpected error");
                    }
                })
                .orElse(null);
    }

    /**
     * Process a callback from an online payment gateway.
     *
     * <p>Idempotency guarantees:
     * <ul>
     *   <li>Already PAID → log and return existing (duplicate callback)</li>
     *   <li>Duplicate {@code providerTxnId} → log and return existing</li>
     *   <li>REFUNDED → reject — a refunded payment must not be overwritten</li>
     * </ul>
     *
     * <p>On SUCCESS: payment → PAID, {@code order.paymentStatus} → PAID.
     * <p>On FAILED: payment → FAILED, {@code order.paymentStatus} → FAILED.
     */
    @Transactional
    public PaymentResponse processCallback(PaymentCallbackRequest request) {
        // TODO(phase-2): HMAC/signature verification must be added here before any
        // business mutation. Each gateway (VNPay, MoMo, etc.) uses a different signing
        // algorithm and secret key. Wire a PaymentGatewaySignatureVerifier when the
        // gateway integration is implemented. Failing signature verification must throw
        // AppException(ErrorCode.PAYMENT_CALLBACK_INVALID) before the order is touched.
        // See docs/security.md §5 and CLAUDE.md §9 for context.

        Order order = orderRepository.findByOrderCode(request.getOrderCode())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_CALLBACK_INVALID,
                        "Order not found: " + request.getOrderCode()));

        // Acquire a row-level lock on the Payment before any idempotency checks.
        // Without this lock, two simultaneous SUCCESS callbacks can both pass the
        // "not PAID" and "duplicate providerTxnId" checks and both write PAID,
        // producing duplicate PaymentTransaction rows and double state mutations.
        Payment payment = paymentRepository.findByOrderIdWithLock(order.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // Idempotent: already paid
        if (payment.getStatus() == PaymentRecordStatus.PAID) {
            log.warn("Gateway callback ignored — payment already PAID: code={}",
                    payment.getPaymentCode());
            return paymentMapper.toResponse(payment);
        }

        // Guard: refunded payments must not be updated via callback
        if (payment.getStatus() == PaymentRecordStatus.REFUNDED
                || payment.getStatus() == PaymentRecordStatus.PARTIALLY_REFUNDED) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "Payment has been refunded and cannot be modified");
        }

        // Idempotent: duplicate provider transaction ID
        if (request.getProviderTxnId() != null
                && transactionRepository.findByProviderTxnId(request.getProviderTxnId()).isPresent()) {
            log.warn("Gateway callback ignored — duplicate providerTxnId: {}",
                    request.getProviderTxnId());
            return paymentMapper.toResponse(payment);
        }

        boolean success = "SUCCESS".equalsIgnoreCase(request.getStatus());

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

        // Record the callback as a transaction entry (immutable audit)
        PaymentTransaction txn = new PaymentTransaction();
        txn.setPayment(payment);
        txn.setTransactionCode(CodeGenerator.generatePaymentTransactionCode());
        txn.setStatus(success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        txn.setAmount(payment.getAmount());
        txn.setMethod(PaymentMethod.ONLINE);
        txn.setProvider(PaymentProviderType.fromValue(request.getProvider()));
        txn.setProviderTxnId(request.getProviderTxnId());
        txn.setReferenceType("CALLBACK");
        txn.setReferenceId(request.getOrderCode());
        txn.setPayload(request.getPayload());
        txn.setNote(success ? "Payment confirmed by gateway" : "Payment rejected by gateway");
        transactionRepository.save(txn);

        log.info("Gateway callback processed: code={} orderCode={} success={}",
                payment.getPaymentCode(), order.getOrderCode(), success);
        return paymentMapper.toResponse(payment);
    }

    // ─── Customer read operations ─────────────────────────────────────────────

    /**
     * Get the payment record for a customer's own order.
     * Enforces ownership — throws ORDER_NOT_FOUND if the order belongs to another customer.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForCustomer(UUID orderId, Customer customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        return paymentMapper.toResponse(findByOrderIdOrThrow(orderId));
    }

    // ─── Admin operations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getPayments(PaymentFilter filter, Pageable pageable) {
        Page<Payment> page = paymentRepository.findAll(
                PaymentSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(paymentMapper::toListItemResponse));
    }

    @Transactional(readOnly = true)
    public PaymentResponse adminGetById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse adminGetByOrderId(UUID orderId) {
        return paymentMapper.toResponse(findByOrderIdOrThrow(orderId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse adminGetByCode(String paymentCode) {
        Payment payment = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(UUID paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return paymentMapper.toTransactionResponses(
                transactionRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Payment findByOrderIdOrThrow(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * Append an immutable transaction record to the payment's audit trail.
     *
     * @param provider      payment provider name (e.g., "VNPAY", "MOMO"), may be null for COD
     * @param providerTxnId provider's own transaction reference, may be null
     */
    private PaymentTransaction recordTransaction(Payment payment, TransactionStatus status,
                                                 PaymentMethod method, String provider,
                                                 String providerTxnId, String note) {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setPayment(payment);
        txn.setTransactionCode(CodeGenerator.generatePaymentTransactionCode());
        txn.setStatus(status);
        txn.setAmount(payment.getAmount());
        txn.setMethod(method);
        txn.setProvider(PaymentProviderType.fromValue(provider));
        txn.setProviderTxnId(providerTxnId);
        txn.setNote(note);
        return transactionRepository.save(txn);
    }
}
