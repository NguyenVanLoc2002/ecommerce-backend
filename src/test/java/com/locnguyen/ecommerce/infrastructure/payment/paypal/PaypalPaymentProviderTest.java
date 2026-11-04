package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.config.PaymentProviderConfigResolver;
import com.locnguyen.ecommerce.domains.payment.config.PaypalResolvedPaymentConfig;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCaptureResult;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCreateResult;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCapture;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCaptureOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCapturePayments;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCapturePurchaseUnit;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderRequest;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaypalPaymentProviderTest {

    @Mock
    private PaymentProviderConfigResolver configResolver;
    @Mock
    private PaypalClient paypalClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaypalResolvedPaymentConfig paypalConfig;
    private PaypalPaymentProvider provider;

    @BeforeEach
    void setUp() {
        paypalConfig = paypalConfig(new BigDecimal("25000"));
        when(configResolver.resolvePaypal()).thenReturn(paypalConfig);
        provider = new PaypalPaymentProvider(configResolver, paypalClient, objectMapper);
    }

    private PaypalResolvedPaymentConfig paypalConfig(BigDecimal rate) {
        return new PaypalResolvedPaymentConfig(
                true,
                "SANDBOX",
                "test-client-id",
                "test-client-secret",
                "https://api-m.sandbox.paypal.com",
                "http://localhost:5173/payment/paypal/return",
                "http://localhost:5173/payment/paypal/cancel",
                "WH-123456",
                "USD",
                "Locen Studio",
                "en-US",
                "PAY_NOW",
                "IMMEDIATE_PAYMENT_REQUIRED",
                "NO_SHIPPING",
                rate,
                30_000,
                30_000
        );
    }

    private Payment payment(String paymentCode, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setPaymentCode(paymentCode);
        payment.setAmount(amount);
        payment.setStatus(PaymentRecordStatus.INITIATED);
        payment.setProviderOrderId("PAYPAL_ORDER_123");
        return payment;
    }

    private Order order(String orderCode) {
        Order order = new Order();
        order.setOrderCode(orderCode);
        return order;
    }

    private PaypalCreateOrderResponse successResponse(String paypalOrderId, String approvalUrl) {
        PaypalLink approveLink = new PaypalLink();
        setField(approveLink, "href", approvalUrl);
        setField(approveLink, "rel", "payer-action");
        setField(approveLink, "method", "GET");

        PaypalCreateOrderResponse response = new PaypalCreateOrderResponse();
        setField(response, "id", paypalOrderId);
        setField(response, "status", "PAYER_ACTION_REQUIRED");
        setField(response, "links", List.of(approveLink));
        return response;
    }

    private PaypalCreateOrderResponse responseWithApproveRel(String paypalOrderId, String approvalUrl) {
        PaypalLink approveLink = new PaypalLink();
        setField(approveLink, "href", approvalUrl);
        setField(approveLink, "rel", "approve");
        setField(approveLink, "method", "GET");

        PaypalCreateOrderResponse response = new PaypalCreateOrderResponse();
        setField(response, "id", paypalOrderId);
        setField(response, "status", "CREATED");
        setField(response, "links", List.of(approveLink));
        return response;
    }

    private PaypalCreateOrderResponse responseWithNoApprovalLink(String paypalOrderId) {
        PaypalCreateOrderResponse response = new PaypalCreateOrderResponse();
        setField(response, "id", paypalOrderId);
        setField(response, "status", "CREATED");
        setField(response, "links", List.of());
        return response;
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

        PaypalCaptureOrderResponse response = new PaypalCaptureOrderResponse();
        setField(response, "id", "PAYPAL_ORDER_123");
        setField(response, "status", captureStatus.equals("COMPLETED") ? "COMPLETED" : "VOIDED");
        setField(response, "purchaseUnits", List.of(unit));
        return response;
    }

    private PaypalCaptureOrderResponse captureResponseNoPurchaseUnits() {
        PaypalCaptureOrderResponse response = new PaypalCaptureOrderResponse();
        setField(response, "id", "PAYPAL_ORDER_123");
        setField(response, "status", "VOIDED");
        setField(response, "purchaseUnits", List.of());
        return response;
    }

    @Test
    void getProviderName_returnsPaypal() {
        assertThat(provider.getProviderName()).isEqualTo("PAYPAL");
    }

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
        void returnsApprovalUrl_whenPaypalRespondsWithLegacyApproveRel() {
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
        void storesPaypalIds_onSuccess() {
            when(paypalClient.createOrder(any(), any()))
                    .thenReturn(successResponse("PAYPAL_ORDER_789",
                            "https://www.sandbox.paypal.com/checkoutnow?token=XYZ"));

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null);

            assertThat(result.getProviderOrderId()).isEqualTo("PAYPAL_ORDER_789");
            assertThat(result.getProviderRequestId()).isEqualTo("PAYPAL_ORDER_789");
        }

        @Test
        void doesNotMarkPaymentPaid_afterSuccessfulCreate() {
            when(paypalClient.createOrder(any(), any()))
                    .thenReturn(successResponse("PAYPAL_ORDER_123",
                            "https://www.sandbox.paypal.com/checkoutnow?token=ABC"));

            Payment payment = payment("PAY001", new BigDecimal("500000"));
            PaymentRecordStatus statusBefore = payment.getStatus();

            provider.createPayment(payment, order("ORD001"), null, null);

            assertThat(payment.getStatus()).isEqualTo(statusBefore);
        }

        @Test
        void setsCustomId_equalToOrderCode_inPurchaseUnit() {
            when(paypalClient.createOrder(any(), eq("ORD001")))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest request = invocation.getArgument(0);
                        assertThat(request.getPurchaseUnits().get(0).getCustomId()).isEqualTo("ORD001");
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null);
        }

        @Test
        void throwsPaymentFailed_whenApprovalLinkMissing() {
            when(paypalClient.createOrder(any(), eq("ORD001")))
                    .thenReturn(responseWithNoApprovalLink("PAYPAL_ORDER_123"));

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throwsPaymentFailed_whenPaypalClientThrows() {
            when(paypalClient.createOrder(any(), any()))
                    .thenThrow(new AppException(ErrorCode.PAYMENT_FAILED, "PayPal API timeout"));

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throwsPaymentCurrencyUnsupported_whenConversionRateMissing() {
            when(configResolver.resolvePaypal()).thenReturn(paypalConfig(null));

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_CURRENCY_UNSUPPORTED);

            verify(paypalClient, never()).createOrder(any(), any());
        }

        @Test
        void convertsVndToUsd_andReturnsProviderSnapshot() {
            when(paypalClient.createOrder(any(), eq("ORD001")))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest request = invocation.getArgument(0);
                        assertThat(request.getPurchaseUnits().get(0).getAmount().getValue()).isEqualTo("20.00");
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null);

            assertThat(result.getProviderAmount()).isEqualByComparingTo("20.00");
            assertThat(result.getProviderCurrency()).isEqualTo("USD");
            assertThat(result.getProviderExchangeRate()).isEqualByComparingTo("25000");
        }

        @Test
        void usesResolvedReturnUrl_whenRequestReturnUrlIsNull() {
            when(paypalClient.createOrder(any(), any()))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest request = invocation.getArgument(0);
                        String returnUrl = request.getPaymentSource().getPaypal().getExperienceContext().getReturnUrl();
                        assertThat(returnUrl).isEqualTo(paypalConfig.returnUrl());
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null);
        }

        @Test
        void usesRequestReturnUrl_whenProvided() {
            String customReturn = "https://shop.example.com/payment/paypal/return";
            when(paypalClient.createOrder(any(), any()))
                    .thenAnswer(invocation -> {
                        PaypalCreateOrderRequest request = invocation.getArgument(0);
                        String returnUrl = request.getPaymentSource().getPaypal().getExperienceContext().getReturnUrl();
                        assertThat(returnUrl).isEqualTo(customReturn);
                        return successResponse("PAYPAL_ORDER_123",
                                "https://www.sandbox.paypal.com/checkoutnow?token=ABC");
                    });

            provider.createPayment(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    customReturn,
                    null);
        }
    }

    @Nested
    class CreatePaymentUrl {

        @Test
        void returnsApprovalUrl_delegatingToCreatePayment() {
            String expectedUrl = "https://www.sandbox.paypal.com/checkoutnow?token=URL";
            when(paypalClient.createOrder(any(), any()))
                    .thenReturn(successResponse("PAYPAL_ORDER_000", expectedUrl));

            String url = provider.createPaymentUrl(
                    payment("PAY001", new BigDecimal("500000")),
                    order("ORD001"),
                    null,
                    null);

            assertThat(url).isEqualTo(expectedUrl);
        }
    }

    @Nested
    class CapturePayment {

        @Test
        void returnsSuccess_whenCaptureCompleted() {
            when(paypalClient.captureOrder(eq("PAYPAL_ORDER_123")))
                    .thenReturn(captureResponse("CAPTURE_ID_001", "COMPLETED"));

            Optional<PaymentProviderCaptureResult> result = provider.capturePayment(
                    payment("PAY001", new BigDecimal("500000")),
                    "PAYPAL_ORDER_123");

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
                    payment("PAY001", new BigDecimal("500000")),
                    "PAYPAL_ORDER_123");

            assertThat(result).isPresent();
            assertThat(result.get().isSuccess()).isFalse();
            assertThat(result.get().getProviderTxnId()).isEqualTo("CAPTURE_ID_002");
        }

        @Test
        void returnsFailure_whenNoCaptureInResponse() {
            when(paypalClient.captureOrder(any()))
                    .thenReturn(captureResponseNoPurchaseUnits());

            Optional<PaymentProviderCaptureResult> result = provider.capturePayment(
                    payment("PAY001", new BigDecimal("500000")),
                    "PAYPAL_ORDER_123");

            assertThat(result).isPresent();
            assertThat(result.get().isSuccess()).isFalse();
            assertThat(result.get().getProviderTxnId()).isNull();
        }

        @Test
        void propagatesAppException_whenClientThrows() {
            when(paypalClient.captureOrder(any()))
                    .thenThrow(new AppException(ErrorCode.PAYMENT_FAILED, "PayPal timeout"));

            assertThatThrownBy(() -> provider.capturePayment(
                    payment("PAY001", new BigDecimal("500000")),
                    "PAYPAL_ORDER_123"))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }
    }

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
            when(paypalClient.verifyWebhookSignature(any(), any(), any(), any(), any(), any())).thenReturn(true);

            assertThat(provider.verifySignature(
                    "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}",
                    validHeadersJson())).isTrue();
        }

        @Test
        void returnsFalse_whenHeadersMissingOrInvalid() {
            assertThat(provider.verifySignature("{\"event_type\":\"test\"}", null)).isFalse();
            assertThat(provider.verifySignature("{\"event_type\":\"test\"}", "   ")).isFalse();
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

            assertThat(provider.verifySignature("{}", validHeadersJson())).isTrue();
        }
    }

    @Nested
    class IsSuccess {

        @Test
        void returnsTrue_forCaptureCompletedEvent() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                    + "\"resource\":{\"id\":\"CAP001\",\"status\":\"COMPLETED\",\"custom_id\":\"ORD001\"}}";
            assertThat(provider.isSuccess(payload)).isTrue();
        }

        @Test
        void returnsFalse_whenPayloadDoesNotRepresentSuccessfulCapture() {
            assertThat(provider.isSuccess("{\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}")).isFalse();
            assertThat(provider.isSuccess(
                    "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{\"status\":\"DECLINED\"}}"))
                    .isFalse();
            assertThat(provider.isSuccess("not-json")).isFalse();
        }
    }

    @Nested
    class ExtractProviderTxnId {

        @Test
        void returnsCaptureId_fromResource() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                    + "\"resource\":{\"id\":\"CAPTURE_ID_XYZ\",\"status\":\"COMPLETED\"}}";
            assertThat(provider.extractProviderTxnId(payload)).isEqualTo("CAPTURE_ID_XYZ");
        }

        @Test
        void returnsNull_whenPayloadInvalidOrMissingResource() {
            assertThat(provider.extractProviderTxnId("{\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}")).isNull();
            assertThat(provider.extractProviderTxnId("not-json")).isNull();
        }
    }

    @Nested
    class ExtractOrderCode {

        @Test
        void returnsCustomId_fromResource() {
            String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                    + "\"resource\":{\"id\":\"CAP001\",\"status\":\"COMPLETED\",\"custom_id\":\"ORD-2025-001\"}}";
            assertThat(provider.extractOrderCode(payload)).isEqualTo("ORD-2025-001");
        }

        @Test
        void returnsNull_whenPayloadInvalidOrMissingCustomId() {
            assertThat(provider.extractOrderCode("{\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}")).isNull();
            assertThat(provider.extractOrderCode("bad-payload")).isNull();
        }
    }

    @Test
    void extractAmount_alwaysReturnsNull() {
        assertThat(provider.extractAmount("{\"resource\":{\"amount\":{\"currency_code\":\"USD\",\"value\":\"10.00\"}}}"))
                .isNull();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception exception) {
            throw new RuntimeException("Could not set field " + fieldName, exception);
        }
    }
}
