package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCaptureResult;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCreateResult;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaypalPaymentProvider}.
 * PayPal HTTP calls are mocked through {@link PaypalClient}.
 */
class PaypalPaymentProviderTest {

    private PaypalPaymentProperties properties;
    private PaypalClient paypalClient;
    private PaypalPaymentProvider provider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new PaypalPaymentProperties();
        properties.setEnabled(true);
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");
        properties.setReturnUrl("http://localhost:5173/payment/paypal/return");
        properties.setCancelUrl("http://localhost:5173/payment/paypal/cancel");
        properties.setWebhookId("WH-123456");
        properties.setCurrency("USD");
        properties.setTestConversionEnabled(true);
        properties.setTestConversionRateVndToUsd(new BigDecimal("25000"));
        properties.setConnectTimeoutMs(30_000);
        properties.setReadTimeoutMs(30_000);

        paypalClient = mock(PaypalClient.class);
        provider = new PaypalPaymentProvider(properties, paypalClient, objectMapper);
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private Payment payment(String paymentCode, BigDecimal amount) {
        Payment p = new Payment();
        p.setPaymentCode(paymentCode);
        p.setAmount(amount);
        p.setStatus(PaymentRecordStatus.INITIATED);
        p.setProviderOrderId("PAYPAL_ORDER_123");
        return p;
    }

    private Order order(String orderCode) {
        Order o = new Order();
        o.setOrderCode(orderCode);
        return o;
    }

    private PaypalCreateOrderResponse successResponse(String paypalOrderId, String approvalUrl) {
        PaypalLink approveLink = new PaypalLink();
        setField(approveLink, "href", approvalUrl);
        setField(approveLink, "rel", "payer-action");
        setField(approveLink, "method", "GET");

        PaypalCreateOrderResponse resp = new PaypalCreateOrderResponse();
        setField(resp, "id", paypalOrderId);
        setField(resp, "status", "PAYER_ACTION_REQUIRED");
        setField(resp, "links", List.of(approveLink));
        return resp;
    }

    private PaypalCreateOrderResponse responseWithApproveRel(String paypalOrderId, String approvalUrl) {
        PaypalLink approveLink = new PaypalLink();
        setField(approveLink, "href", approvalUrl);
        setField(approveLink, "rel", "approve");
        setField(approveLink, "method", "GET");

        PaypalCreateOrderResponse resp = new PaypalCreateOrderResponse();
        setField(resp, "id", paypalOrderId);
        setField(resp, "status", "CREATED");
        setField(resp, "links", List.of(approveLink));
        return resp;
    }

    private PaypalCreateOrderResponse responseWithNoApprovalLink(String paypalOrderId) {
        PaypalCreateOrderResponse resp = new PaypalCreateOrderResponse();
        setField(resp, "id", paypalOrderId);
        setField(resp, "status", "CREATED");
        setField(resp, "links", List.of());
        return resp;
    }

    private PaypalCaptureOrderResponse captureResponse(String captureId, String captureStatus) {
        PaypalCapture capture = new PaypalCapture();
        setField(capture, "id", captureId);
        setField(capture, "status", captureStatus);
        setField(capture, "customId", "ORD001");

        PaypalCapturePayments payments = new PaypalCapturePayments();
        setField(payments, "captures", List.of(capture));

        PaypalCapturePurchaseUnit unit = new PaypalCapturePurchaseUnit();
        setField(unit, "payments", payments);

        PaypalCaptureOrderResponse resp = new PaypalCaptureOrderResponse();
        setField(resp, "id", "PAYPAL_ORDER_123");
        setField(resp, "status", captureStatus.equals("COMPLETED") ? "COMPLETED" : "VOIDED");
        setField(resp, "purchaseUnits", List.of(unit));
        return resp;
    }

    private PaypalCaptureOrderResponse captureResponseNoPurchaseUnits() {
        PaypalCaptureOrderResponse resp = new PaypalCaptureOrderResponse();
        setField(resp, "id", "PAYPAL_ORDER_123");
        setField(resp, "status", "VOIDED");
        setField(resp, "purchaseUnits", List.of());
        return resp;
    }

    // ─── getProviderName ──────────────────────────────────────────────────────

    @Test
    void getProviderName_returnsPAYPAL() {
        assertThat(provider.getProviderName()).isEqualTo("PAYPAL");
    }

    // ─── createPayment ────────────────────────────────────────────────────────

    @Nested
    class CreatePayment {

        @Test
        void returnsApprovalUrl_whenPaypalRespondsWithPayerActionLink() {
            String expectedUrl = "https://www.sandbox.paypal.com/checkoutnow?token=ABC123";
            when(paypalClient.createOrder(any(), eq("ORD001")))
                    .thenReturn(successResponse("PAYPAL_ORDER_123", expectedUrl));

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    "http://localhost:5173/return",
                    "https://example.com/webhooks/PAYPAL");

            assertThat(result.getPaymentUrl()).isEqualTo(expectedUrl);
        }

        @Test
        void returnsApprovalUrl_whenPaypalRespondsWithApproveRelLegacy() {
            String expectedUrl = "https://www.sandbox.paypal.com/checkoutnow?token=LEGACY";
            when(paypalClient.createOrder(any(), eq("ORD002")))
                    .thenReturn(responseWithApproveRel("PAYPAL_ORDER_456", expectedUrl));

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY002", new BigDecimal("250000")),
                    order("ORD002"),
                    null,
                    "https://example.com/webhooks/PAYPAL");

            assertThat(result.getPaymentUrl()).isEqualTo(expectedUrl);
        }

        @Test
        void storesPaypalOrderId_asProviderOrderId() {
            when(paypalClient.createOrder(any(), any()))
                    .thenReturn(successResponse("PAYPAL_ORDER_789",
                            "https://www.sandbox.paypal.com/checkoutnow?token=XYZ"));

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null);

            assertThat(result.getProviderOrderId()).isEqualTo("PAYPAL_ORDER_789");
        }

        @Test
        void storesPaypalOrderId_asProviderRequestId() {
            when(paypalClient.createOrder(any(), any()))
                    .thenReturn(successResponse("PAYPAL_ORDER_789",
                            "https://www.sandbox.paypal.com/checkoutnow?token=XYZ"));

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null);

            assertThat(result.getProviderRequestId()).isEqualTo("PAYPAL_ORDER_789");
        }

        @Test
        void doesNotMarkPaymentPaid_afterSuccessfulCreate() {
            when(paypalClient.createOrder(any(), any()))
                    .thenReturn(successResponse("PAYPAL_ORDER_123",
                            "https://www.sandbox.paypal.com/checkoutnow?token=ABC"));

            Payment p = payment("PAY001", new BigDecimal("500000"));
            PaymentRecordStatus statusBefore = p.getStatus();
            provider.createPayment(p, order("ORD001"), null, null);

            assertThat(p.getStatus()).isEqualTo(statusBefore);
        }

        @Test
        void setsCustomId_equalToOrderCode_inPurchaseUnit() {
            when(paypalClient.createOrder(any(), eq("ORD001")))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest req = invocation.getArgument(0);
                        String customId = req.getPurchaseUnits().get(0).getCustomId();
                        assertThat(customId).isEqualTo("ORD001");
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null);
        }

        @Test
        void throws_PAYMENT_FAILED_whenApprovalLinkMissing() {
            when(paypalClient.createOrder(any(), eq("ORD001")))
                    .thenReturn(responseWithNoApprovalLink("PAYPAL_ORDER_123"));

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throws_PAYMENT_FAILED_whenPaypalClientThrows() {
            when(paypalClient.createOrder(any(), any()))
                    .thenThrow(new AppException(ErrorCode.PAYMENT_FAILED, "PayPal API timeout"));

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throws_PAYMENT_CURRENCY_UNSUPPORTED_whenTestConversionDisabled() {
            properties.setTestConversionEnabled(false);

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_CURRENCY_UNSUPPORTED);

            verify(paypalClient, never()).createOrder(any(), any());
        }

        @Test
        void convertsVndToUsd_whenTestConversionEnabled() {
            // 500,000 VND ÷ 25,000 = 20.00 USD
            when(paypalClient.createOrder(any(), eq("ORD001")))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest req = invocation.getArgument(0);
                        String value = req.getPurchaseUnits().get(0).getAmount().getValue();
                        assertThat(value).isEqualTo("20.00");
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null);
        }

        @Test
        void usesConfiguredReturnUrl_whenRequestReturnUrlIsNull() {
            when(paypalClient.createOrder(any(), any()))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest req = invocation.getArgument(0);
                        String returnUrl = req.getPaymentSource().getPaypal()
                                .getExperienceContext().getReturnUrl();
                        assertThat(returnUrl).isEqualTo(properties.getReturnUrl());
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null);
        }

        @Test
        void usesRequestReturnUrl_whenProvided() {
            String customReturn = "https://shop.example.com/payment/paypal/return";
            when(paypalClient.createOrder(any(), any()))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest req = invocation.getArgument(0);
                        String returnUrl = req.getPaymentSource().getPaypal()
                                .getExperienceContext().getReturnUrl();
                        assertThat(returnUrl).isEqualTo(customReturn);
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), customReturn, null);
        }
    }

    // ─── createPaymentUrl ─────────────────────────────────────────────────────

    @Nested
    class CreatePaymentUrl {

        @Test
        void returnsApprovalUrl_delegatingToCreatePayment() {
            String expectedUrl = "https://www.sandbox.paypal.com/checkoutnow?token=URL";
            when(paypalClient.createOrder(any(), any()))
                    .thenReturn(successResponse("PAYPAL_ORDER_000", expectedUrl));

            String url = provider.createPaymentUrl(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"), null, null);

            assertThat(url).isEqualTo(expectedUrl);
        }
    }

    // ─── capturePayment ───────────────────────────────────────────────────────

    @Nested
    class CapturePayment {

        @Test
        void returnsSuccess_whenCaptureCompleted() {
            when(paypalClient.captureOrder(eq("PAYPAL_ORDER_123")))
                    .thenReturn(captureResponse("CAPTURE_ID_001", "COMPLETED"));

            Optional<PaymentProviderCaptureResult> result = provider.capturePayment(
                    payment("PAY001", new BigDecimal("500000")), "PAYPAL_ORDER_123");

            assertThat(result).isPresent();
            assertThat(result.get().isSuccess()).isTrue();
            assertThat(result.get().getProviderTxnId()).isEqualTo("CAPTURE_ID_001");
            assertThat(result.get().getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        void returnsFailure_whenCaptureDeclined() {
            when(paypalClient.captureOrder(eq("PAYPAL_ORDER_123")))
                    .thenReturn(captureResponse("CAPTURE_ID_002", "DECLINED"));

            Optional<PaymentProviderCaptureResult> result = provider.capturePayment(
                    payment("PAY001", new BigDecimal("500000")), "PAYPAL_ORDER_123");

            assertThat(result).isPresent();
            assertThat(result.get().isSuccess()).isFalse();
            assertThat(result.get().getProviderTxnId()).isEqualTo("CAPTURE_ID_002");
        }

        @Test
        void returnsFailure_whenNoCaptureInResponse() {
            when(paypalClient.captureOrder(any()))
                    .thenReturn(captureResponseNoPurchaseUnits());

            Optional<PaymentProviderCaptureResult> result = provider.capturePayment(
                    payment("PAY001", new BigDecimal("500000")), "PAYPAL_ORDER_123");

            assertThat(result).isPresent();
            assertThat(result.get().isSuccess()).isFalse();
            assertThat(result.get().getProviderTxnId()).isNull();
        }

        @Test
        void propagatesAppException_whenClientThrows() {
            when(paypalClient.captureOrder(any()))
                    .thenThrow(new AppException(ErrorCode.PAYMENT_FAILED, "PayPal timeout"));

            assertThatThrownBy(() -> provider.capturePayment(
                    payment("PAY001", new BigDecimal("500000")), "PAYPAL_ORDER_123"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }
    }

    // ─── verifySignature ─────────────────────────────────────────────────────

    @Nested
    class VerifySignature {

        private String validHeadersJson() {
            return "{\"paypal_auth_algo\":\"SHA256withRSA\","
                    + "\"paypal_cert_url\":\"https://api.paypal.com/cert\","
                    + "\"paypal_transmission_id\":\"TXN-001\","
                    + "\"paypal_transmission_sig\":\"sig-value\","
                    + "\"paypal_transmission_time\":\"2025-01-01T00:00:00Z\"}";
        }

        @Test
        void returnsTrue_whenPaypalVerifyReturnsSuccess() {
            when(paypalClient.verifyWebhookSignature(any(), any(), any(), any(), any(), any()))
                    .thenReturn(true);

            assertThat(provider.verifySignature("{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}",
                    validHeadersJson())).isTrue();
        }

        @Test
        void returnsFalse_whenPaypalVerifyReturnsFalse() {
            when(paypalClient.verifyWebhookSignature(any(), any(), any(), any(), any(), any()))
                    .thenReturn(false);

            assertThat(provider.verifySignature("{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}",
                    validHeadersJson())).isFalse();
        }

        @Test
        void returnsFalse_whenSignatureIsNull() {
            assertThat(provider.verifySignature("{\"event_type\":\"test\"}", null)).isFalse();
            verify(paypalClient, never()).verifyWebhookSignature(any(), any(), any(), any(), any(), any());
        }

        @Test
        void returnsFalse_whenSignatureIsBlank() {
            assertThat(provider.verifySignature("{\"event_type\":\"test\"}", "   ")).isFalse();
            verify(paypalClient, never()).verifyWebhookSignature(any(), any(), any(), any(), any(), any());
        }

        @Test
        void returnsFalse_whenSignatureIsInvalidJson() {
            assertThat(provider.verifySignature("{\"event_type\":\"test\"}", "not-json")).isFalse();
            verify(paypalClient, never()).verifyWebhookSignature(any(), any(), any(), any(), any(), any());
        }

        @Test
        void passesCorrectHeadersToClient() {
            when(paypalClient.verifyWebhookSignature(
                    eq("SHA256withRSA"),
                    eq("https://api.paypal.com/cert"),
                    eq("TXN-001"),
                    eq("sig-value"),
                    eq("2025-01-01T00:00:00Z"),
                    any()))
                    .thenReturn(true);

            boolean result = provider.verifySignature("{}", validHeadersJson());

            assertThat(result).isTrue();
        }
    }

    // ─── isSuccess ────────────────────────────────────────────────────────────

    @Nested
    class IsSuccess {

        @Test
        void returnsTrue_forCaptureCompletedEvent() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                    + "\"resource\":{\"id\":\"CAP001\",\"status\":\"COMPLETED\",\"custom_id\":\"ORD001\"}}";
            assertThat(provider.isSuccess(payload)).isTrue();
        }

        @Test
        void returnsFalse_whenEventTypeIsDifferent() {
            String payload = "{\"event_type\":\"CHECKOUT.ORDER.APPROVED\","
                    + "\"resource\":{\"id\":\"ORD001\",\"status\":\"APPROVED\"}}";
            assertThat(provider.isSuccess(payload)).isFalse();
        }

        @Test
        void returnsFalse_whenCaptureStatusIsNotCompleted() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                    + "\"resource\":{\"id\":\"CAP001\",\"status\":\"DECLINED\"}}";
            assertThat(provider.isSuccess(payload)).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadIsInvalidJson() {
            assertThat(provider.isSuccess("not-json")).isFalse();
        }

        @Test
        void returnsFalse_whenResourceIsNull() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}";
            assertThat(provider.isSuccess(payload)).isFalse();
        }
    }

    // ─── extractProviderTxnId ─────────────────────────────────────────────────

    @Nested
    class ExtractProviderTxnId {

        @Test
        void returnsCaptureId_fromResource() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                    + "\"resource\":{\"id\":\"CAPTURE_ID_XYZ\",\"status\":\"COMPLETED\"}}";
            assertThat(provider.extractProviderTxnId(payload)).isEqualTo("CAPTURE_ID_XYZ");
        }

        @Test
        void returnsNull_whenResourceAbsent() {
            String payload = "{\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}";
            assertThat(provider.extractProviderTxnId(payload)).isNull();
        }

        @Test
        void returnsNull_whenPayloadIsInvalidJson() {
            assertThat(provider.extractProviderTxnId("not-json")).isNull();
        }
    }

    // ─── extractOrderCode ─────────────────────────────────────────────────────

    @Nested
    class ExtractOrderCode {

        @Test
        void returnsCustomId_fromResource() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                    + "\"resource\":{\"id\":\"CAP001\",\"status\":\"COMPLETED\",\"custom_id\":\"ORD-2025-001\"}}";
            assertThat(provider.extractOrderCode(payload)).isEqualTo("ORD-2025-001");
        }

        @Test
        void returnsNull_whenCustomIdAbsent() {
            String payload = "{\"event_type\":\"CHECKOUT.ORDER.APPROVED\","
                    + "\"resource\":{\"id\":\"ORDER001\",\"status\":\"APPROVED\"}}";
            assertThat(provider.extractOrderCode(payload)).isNull();
        }

        @Test
        void returnsNull_whenPayloadIsInvalidJson() {
            assertThat(provider.extractOrderCode("bad-payload")).isNull();
        }
    }

    // ─── extractAmount ────────────────────────────────────────────────────────

    @Test
    void extractAmount_alwaysReturnsNull() {
        // PayPal amounts are in USD; stored payments are in VND.
        // Amount guard is skipped for PayPal webhooks by returning null.
        String payload = "{\"resource\":{\"amount\":{\"currency_code\":\"USD\",\"value\":\"10.00\"}}}";
        assertThat(provider.extractAmount(payload)).isNull();
    }

    // ─── Helper to set fields on response DTOs (no setters) ──────────────────

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }
}
