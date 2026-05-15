package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCaptureOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookVerifyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the capture and webhook verification methods of {@link PaypalClient}.
 */
class PaypalClientCaptureTest {

    private PaypalPaymentProperties properties;
    private PaypalClient paypalClient;

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private PaypalOAuthClient oAuthClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new PaypalPaymentProperties();
        properties.setEnabled(true);
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-secret");
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");
        properties.setCurrency("USD");
        properties.setWebhookId("WH-12345");
        properties.setTestConversionEnabled(true);
        properties.setTestConversionRateVndToUsd(new BigDecimal("25000"));
        properties.setConnectTimeoutMs(30_000);
        properties.setReadTimeoutMs(30_000);

        oAuthClient = mock(PaypalOAuthClient.class);
        when(oAuthClient.getAccessToken()).thenReturn("test-access-token");

        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).post();
        doReturn(bodySpec).when(uriSpec).uri(any(String.class));
        doReturn(bodySpec).when(bodySpec).header(any(), any());
        doReturn(bodySpec).when(bodySpec).contentType(any(MediaType.class));
        doReturn(bodySpec).when(bodySpec).body(any(Object.class));
        doReturn(responseSpec).when(bodySpec).retrieve();

        paypalClient = new PaypalClient(properties, oAuthClient, restClient, objectMapper);
    }

    private PaypalCaptureOrderResponse emptyCaptureResponse() {
        return new PaypalCaptureOrderResponse();
    }

    private PaypalWebhookVerifyResponse verifyResponse(String status) {
        PaypalWebhookVerifyResponse resp = new PaypalWebhookVerifyResponse();
        setField(resp, "verificationStatus", status);
        return resp;
    }

    // ─── captureOrder ─────────────────────────────────────────────────────────

    @Nested
    class CaptureOrder {

        @Test
        void returnsResponse_whenPaypalResponds() {
            PaypalCaptureOrderResponse expected = emptyCaptureResponse();
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(expected);

            PaypalCaptureOrderResponse result = paypalClient.captureOrder("PAYPAL_ORDER_001");

            assertThat(result).isSameAs(expected);
        }

        @Test
        void throws_PAYMENT_FAILED_whenResponseIsNull() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(null);

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throws_PAYMENT_FAILED_onRestClientException() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class))
                    .thenThrow(new RestClientException("Connection refused"));

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void rethrowsAppException_fromRestClient() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class))
                    .thenThrow(new AppException(ErrorCode.PAYMENT_FAILED, "Already captured"));

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void includesCaptureUriPath_inRequest() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(emptyCaptureResponse());

            paypalClient.captureOrder("ORDER-XYZ-789");

            verify(uriSpec).uri(contains("/v2/checkout/orders/ORDER-XYZ-789/capture"));
        }

        @Test
        void sendsContentTypeJson_andAcceptJson_andEmptyBody() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(emptyCaptureResponse());

            paypalClient.captureOrder("PAYPAL_ORDER_001");

            verify(bodySpec).contentType(MediaType.APPLICATION_JSON);
            verify(bodySpec).header("Accept", "application/json");
            verify(bodySpec).body(Map.of());
        }

        @Test
        void throws_PAYMENT_FAILED_on415UnsupportedMediaType() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                            null, "{\"name\":\"UNSUPPORTED_MEDIA_TYPE\"}".getBytes(), null));

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }
    }

    // ─── verifyWebhookSignature ───────────────────────────────────────────────

    @Nested
    class VerifyWebhookSignature {

        private static final String VALID_RAW_BODY = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                + "\"id\":\"WH-001\",\"resource\":{\"id\":\"CAP001\",\"status\":\"COMPLETED\"}}";

        @Test
        void returnsTrue_whenPaypalVerificationSucceeds() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class))
                    .thenReturn(verifyResponse("SUCCESS"));

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA", "https://api.paypal.com/cert",
                    "TXN-001", "sig-value", "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isTrue();
        }

        @Test
        void returnsFalse_whenVerificationStatusIsNotSuccess() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class))
                    .thenReturn(verifyResponse("FAILURE"));

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA", "https://api.paypal.com/cert",
                    "TXN-001", "sig-value", "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenResponseBodyIsNull() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class)).thenReturn(null);

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA", "https://api.paypal.com/cert",
                    "TXN-001", "sig-value", "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_onRestClientException() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class))
                    .thenThrow(new RestClientException("timeout"));

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA", "https://api.paypal.com/cert",
                    "TXN-001", "sig-value", "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenRawBodyIsNotValidJson() {
            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA", "https://api.paypal.com/cert",
                    "TXN-001", "sig-value", "2025-01-01T00:00:00Z",
                    "not-json-body");

            assertThat(result).isFalse();
            // Should not reach the HTTP call if body parsing fails
            verify(responseSpec, never()).body(any(Class.class));
        }

        @Test
        void returnsFalse_whenWebhookIdNotConfigured() {
            properties.setWebhookId("");

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA", "https://api.paypal.com/cert",
                    "TXN-001", "sig-value", "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
            verify(restClient, never()).post();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String contains(String substring) {
        return argThat(s -> s != null && s.contains(substring));
    }

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
