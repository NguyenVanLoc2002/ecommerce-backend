package com.locnguyen.ecommerce.infrastructure.payment.momo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCreateResult;
import com.locnguyen.ecommerce.infrastructure.payment.momo.dto.MomoCreatePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MomoPaymentProvider}.
 *
 * The MoMo HTTP API is mocked — real network calls are never made.
 */
class MomoPaymentProviderTest {

    private MomoPaymentProperties properties;
    private MomoSignatureService signatureService;
    private MomoPaymentProvider provider;

    // RestClient mock chain
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        properties = new MomoPaymentProperties();
        properties.setEnabled(true);
        properties.setPartnerCode("MOMOTEST01");
        properties.setAccessKey("F8BBA842ECF85");
        properties.setSecretKey("K951B6PE1waDMi640xX08PD3vg6EkVlz");
        properties.setCreateUrl("https://test-payment.momo.vn/v2/gateway/api/create");
        properties.setRedirectUrl("http://localhost:5173/payment/momo/return");
        properties.setIpnUrl("https://example.com/api/v1/payments/webhooks/MOMO");
        properties.setRequestType("captureWallet");
        properties.setLang("vi");

        signatureService = new MomoSignatureService();

        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).post();
        doReturn(bodySpec).when(uriSpec).uri(any(URI.class));
        doReturn(bodySpec).when(bodySpec).contentType(any(MediaType.class));
        doReturn(bodySpec).when(bodySpec).body(any(Object.class));
        doReturn(responseSpec).when(bodySpec).retrieve();

        provider = new MomoPaymentProvider(properties, signatureService, new ObjectMapper(), restClient);
    }

    private Payment payment(String paymentCode, BigDecimal amount) {
        Payment p = new Payment();
        p.setPaymentCode(paymentCode);
        p.setAmount(amount);
        return p;
    }

    private Order order(String orderCode) {
        Order o = new Order();
        o.setOrderCode(orderCode);
        return o;
    }

    private MomoCreatePaymentResponse successResponse(String orderId, String payUrl) {
        MomoCreatePaymentResponse resp = new MomoCreatePaymentResponse();
        resp.setResultCode(0);
        resp.setMessage("Successful.");
        resp.setOrderId(orderId);
        resp.setPayUrl(payUrl);
        resp.setDeeplink("momo://pay?orderId=" + orderId);
        resp.setQrCodeUrl("data:image/png;base64,qrdata...");
        return resp;
    }

    private MomoCreatePaymentResponse failureResponse(int code, String message) {
        MomoCreatePaymentResponse resp = new MomoCreatePaymentResponse();
        resp.setResultCode(code);
        resp.setMessage(message);
        return resp;
    }

    // ─── getProviderName ─────────────────────────────────────────────────────

    @Test
    void getProviderName_returnsMOMO() {
        assertThat(provider.getProviderName()).isEqualTo("MOMO");
    }

    // ─── createPayment ────────────────────────────────────────────────────────

    @Nested
    class CreatePayment {

        @Test
        void returnsPaymentUrl_whenMomoReturnsResultCode0() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://test-payment.momo.vn/pay?t=abc");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"),
                    "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO");

            assertThat(result.getPaymentUrl()).isEqualTo("https://test-payment.momo.vn/pay?t=abc");
        }

        @Test
        void returnsDeeplink_whenMomoReturnsResultCode0() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://pay.momo.vn/pay");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO");

            assertThat(result.getDeeplink()).startsWith("momo://");
        }

        @Test
        void returnsQrCodeUrl_whenMomoReturnsResultCode0() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://pay.momo.vn/pay");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO");

            assertThat(result.getQrCodeUrl()).isNotBlank();
        }

        @Test
        void storesProviderOrderId_onSuccess() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://pay.momo.vn/pay");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO");

            assertThat(result.getProviderOrderId()).startsWith("MOMO_ORD001_");
        }

        @Test
        void storesProviderRequestId_onSuccess() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://pay.momo.vn/pay");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            PaymentProviderCreateResult result = provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO");

            assertThat(result.getProviderRequestId()).startsWith("REQ_PAY001_");
        }

        @Test
        void throws_PAYMENT_FAILED_whenResultCodeIsNot0() {
            when(responseSpec.body(MomoCreatePaymentResponse.class))
                    .thenReturn(failureResponse(11, "Invalid access key"));

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throws_PAYMENT_FAILED_onHttpTimeout() {
            when(responseSpec.body(MomoCreatePaymentResponse.class))
                    .thenThrow(new RestClientException("Connection timed out"));

            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throws_PAYMENT_FAILED_whenAmountBelowMinimum() {
            // Amount 999 < 1000 (MoMo minimum)
            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("999")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);

            // No HTTP call should be made for invalid amount
            verify(restClient, never()).post();
        }

        @Test
        void throws_PAYMENT_FAILED_whenAmountExceedsMaximum() {
            // Amount 50_000_001 > 50_000_000 (MoMo maximum)
            assertThatThrownBy(() -> provider.createPayment(
                    payment("PAY001", new BigDecimal("50000001")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);

            verify(restClient, never()).post();
        }

        @Test
        void callsMomoApi_withCorrectContentType() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://pay.momo.vn/pay");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            provider.createPayment(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"), "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO");

            verify(bodySpec).contentType(MediaType.APPLICATION_JSON);
        }

        @Test
        void doesNotMarkPaymentPaid_afterSuccessfulCreate() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://pay.momo.vn/pay");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            Payment p = payment("PAY001", new BigDecimal("50000"));
            PaymentRecordStatus statusBefore = p.getStatus();
            provider.createPayment(p, order("ORD001"),
                    "http://localhost:5173/return", "https://example.com/webhooks/MOMO");

            // Provider must not change the payment status — only IPN/webhook processing may do that
            assertThat(p.getStatus()).isEqualTo(statusBefore);
        }
    }

    // ─── createPaymentUrl ─────────────────────────────────────────────────────

    @Nested
    class CreatePaymentUrl {

        @Test
        void returnsPayUrl_fromMomoResponse() {
            MomoCreatePaymentResponse momoResp = successResponse(
                    "MOMO_ORD001_123", "https://test-payment.momo.vn/pay?t=xyz");
            when(responseSpec.body(MomoCreatePaymentResponse.class)).thenReturn(momoResp);

            String url = provider.createPaymentUrl(
                    payment("PAY001", new BigDecimal("50000")),
                    order("ORD001"),
                    "http://localhost:5173/return",
                    "https://example.com/webhooks/MOMO");

            assertThat(url).isEqualTo("https://test-payment.momo.vn/pay?t=xyz");
        }
    }

    // ─── isSuccess ────────────────────────────────────────────────────────────

    @Nested
    class IsSuccess {

        @Test
        void returnsTrue_whenResultCodeIs0() {
            String payload = "{\"resultCode\":0,\"message\":\"Successful.\"}";
            assertThat(provider.isSuccess(payload)).isTrue();
        }

        @Test
        void returnsFalse_whenResultCodeIsNon0() {
            String payload = "{\"resultCode\":11,\"message\":\"Invalid access key\"}";
            assertThat(provider.isSuccess(payload)).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadIsBlank() {
            assertThat(provider.isSuccess("")).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadIsNull() {
            assertThat(provider.isSuccess(null)).isFalse();
        }

        @Test
        void returnsFalse_whenResultCodeFieldMissing() {
            assertThat(provider.isSuccess("{\"message\":\"ok\"}")).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadMalformed() {
            assertThat(provider.isSuccess("not-json")).isFalse();
        }
    }

    // ─── extractProviderTxnId ─────────────────────────────────────────────────

    @Nested
    class ExtractProviderTxnId {

        @Test
        void returnsTransId_fromValidIpnPayload() {
            String payload = "{\"transId\":3455806203,\"resultCode\":0}";
            assertThat(provider.extractProviderTxnId(payload)).isEqualTo("3455806203");
        }

        @Test
        void returnsNull_whenTransIdMissing() {
            assertThat(provider.extractProviderTxnId("{\"resultCode\":0}")).isNull();
        }

        @Test
        void returnsNull_whenPayloadNull() {
            assertThat(provider.extractProviderTxnId(null)).isNull();
        }

        @Test
        void returnsNull_whenPayloadMalformed() {
            assertThat(provider.extractProviderTxnId("not-json")).isNull();
        }
    }

    // ─── extractOrderCode ────────────────────────────────────────────────────

    @Nested
    class ExtractOrderCode {

        @Test
        void parsesOrderCode_fromProviderOrderId() {
            String payload = "{\"orderId\":\"MOMO_ORD20260514123456_1715700000000\",\"resultCode\":0}";
            assertThat(provider.extractOrderCode(payload)).isEqualTo("ORD20260514123456");
        }

        @Test
        void returnsNull_whenOrderIdMissing() {
            assertThat(provider.extractOrderCode("{\"resultCode\":0}")).isNull();
        }

        @Test
        void returnsNull_whenPayloadNull() {
            assertThat(provider.extractOrderCode(null)).isNull();
        }
    }

    // ─── parseOrderCodeFromProviderOrderId ───────────────────────────────────

    @Nested
    class ParseOrderCode {

        @Test
        void parsesCorrectly_fromFullProviderOrderId() {
            String parsed = MomoPaymentProvider.parseOrderCodeFromProviderOrderId(
                    "MOMO_ORD20260514123456_1715700000000");
            assertThat(parsed).isEqualTo("ORD20260514123456");
        }

        @Test
        void returnsWithoutPrefix_whenNoTimestampSuffix() {
            // Handles edge case if format ever lacks timestamp
            String parsed = MomoPaymentProvider.parseOrderCodeFromProviderOrderId("MOMO_ORD20260514123456");
            assertThat(parsed).isEqualTo("ORD20260514123456");
        }

        @Test
        void returnsNull_whenInputIsNull() {
            assertThat(MomoPaymentProvider.parseOrderCodeFromProviderOrderId(null)).isNull();
        }
    }

    // ─── verifySignature ─────────────────────────────────────────────────────

    @Nested
    class VerifySignature {

        @Test
        void returnsFalse_whenRawBodyIsNull() {
            assertThat(provider.verifySignature(null, "some-sig")).isFalse();
        }

        @Test
        void returnsFalse_whenRawBodyIsBlank() {
            assertThat(provider.verifySignature("  ", "some-sig")).isFalse();
        }

        @Test
        void returnsFalse_whenSignatureIsNull() {
            assertThat(provider.verifySignature("{\"resultCode\":0}", null)).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadMalformed() {
            assertThat(provider.verifySignature("not-json", "sig")).isFalse();
        }
    }
}
